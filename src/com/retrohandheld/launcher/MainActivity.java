package com.retrohandheld.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        enterImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0a0a12"));
        webView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                android.util.Log.d("RetroStation", cm.message()
                    + " -- line " + cm.lineNumber() + " of " + cm.sourceId());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Intercept cover image requests and serve from Java (which has file access)
                if (url.contains("covers/") && url.endsWith(".png")) {
                    try {
                        String path = java.net.URLDecoder.decode(
                            url.replace("file://", ""), "UTF-8");
                        // Resolve /sdcard to actual storage path
                        if (path.startsWith("/sdcard/")) {
                            path = "/storage/emulated/0/" + path.substring(8);
                        }
                        java.io.File f = new java.io.File(path);
                        android.util.Log.d("RetroStation", "Cover file: " + path + " exists=" + f.exists() + " len=" + f.length());
                        if (f.exists() && f.length() > 0) {
                            java.io.FileInputStream fis = new java.io.FileInputStream(f);
                            return new android.webkit.WebResourceResponse(
                                "image/png", "UTF-8", 200, "OK",
                                java.util.Collections.singletonMap("Access-Control-Allow-Origin", "*"),
                                fis);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RetroStation", "Cover load error: " + e.getMessage());
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.addJavascriptInterface(new ConsoleBridge(this, webView), "Console");

        setContentView(webView);

        // Write HTML to file so images can load from same origin
        try {
            java.io.File htmlFile = new java.io.File("/sdcard/RetroHandheld/launcher.html");
            java.io.FileWriter fw = new java.io.FileWriter(htmlFile);
            fw.write(LauncherHTML.get());
            fw.close();
            webView.loadUrl("file:///sdcard/RetroHandheld/launcher.html");
        } catch (Exception e) {
            // Fallback to data URL
            webView.loadDataWithBaseURL(
                "file:///sdcard/RetroHandheld/",
                LauncherHTML.get(), "text/html", "UTF-8", null);
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            webView.evaluateJavascript("if(typeof onResume==='function')onResume()", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (webView != null) {
            webView.evaluateJavascript("if(typeof onResume==='function')onResume()", null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();

            // D-pad navigation
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                webView.evaluateJavascript("navigate('left')", null);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                webView.evaluateJavascript("navigate('right')", null);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                webView.evaluateJavascript("navigate('up')", null);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                webView.evaluateJavascript("navigate('down')", null);
                return true;
            }

            // A / Cross = Select
            if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_SPACE
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                webView.evaluateJavascript("selectCurrent()", null);
                return true;
            }

            // B / Circle = Back
            if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK) {
                webView.evaluateJavascript("goBack()", null);
                return true;
            }

            // L1 = Previous category
            if (code == KeyEvent.KEYCODE_BUTTON_L1) {
                webView.evaluateJavascript("navigate('l1')", null);
                return true;
            }

            // R1 = Next category
            if (code == KeyEvent.KEYCODE_BUTTON_R1) {
                webView.evaluateJavascript("navigate('r1')", null);
                return true;
            }

            // Start = Toggle menu/store
            if (code == KeyEvent.KEYCODE_BUTTON_START) {
                webView.evaluateJavascript("toggleMenu()", null);
                return true;
            }

            // Y / Triangle = Show info
            if (code == KeyEvent.KEYCODE_BUTTON_Y) {
                webView.evaluateJavascript("showInfo()", null);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
