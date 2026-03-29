package com.retrohandheld.launcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.InputDevice;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.File;
import java.util.Set;

public class ConsoleBridge {

    private Activity activity;
    private WebView webView;

    private static final String CORES_PATH = "/data/local/tmp/";
    private static final String CONFIG_PATH = "/sdcard/Android/data/com.retroarch/files/retroarch.cfg";
    private static final String ROMS_BASE = "/sdcard/RetroHandheld/roms";
    private static final String COVERS_BASE = "/sdcard/RetroHandheld/covers";
    private static final String GAME_MAPPING_PATH = "/sdcard/RetroHandheld/game_mapping.json";

    private static final String[] ROM_EXTENSIONS = {
        ".gb", ".gbc", ".gba", ".nes", ".sfc", ".smc",
        ".bin", ".md", ".gen", ".n64", ".z64", ".v64",
        ".iso", ".cso", ".pbp", ".chd", ".cue",
        ".cdi", ".gdi", ".wad", ".zip", ".7z",
        ".nds", ".ds"
    };

    public ConsoleBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public String getGames() {
        StringBuilder json = new StringBuilder("[");
        File romsDir = new File(ROMS_BASE);
        boolean first = true;

        if (romsDir.exists() && romsDir.isDirectory()) {
            File[] systemDirs = romsDir.listFiles();
            if (systemDirs != null) {
                for (int i = 0; i < systemDirs.length; i++) {
                    File systemDir = systemDirs[i];
                    if (!systemDir.isDirectory()) continue;
                    String system = systemDir.getName();
                    first = scanDirectory(json, systemDir, system, first);
                }
            }
        }
        json.append("]");
        return json.toString();
    }

    private boolean scanDirectory(StringBuilder json, File dir, String system, boolean first) {
        File[] files = dir.listFiles();
        if (files == null) return first;

        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                first = scanDirectory(json, f, system, first);
                continue;
            }

            String name = f.getName().toLowerCase();
            boolean isRom = false;
            for (int e = 0; e < ROM_EXTENSIONS.length; e++) {
                if (name.endsWith(ROM_EXTENSIONS[e])) {
                    isRom = true;
                    break;
                }
            }
            if (!isRom) continue;

            if (!first) json.append(",");
            first = false;

            String fileName = f.getName();
            int dotIdx = fileName.lastIndexOf('.');
            String cleanName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

            // Split camelCase
            cleanName = cleanName.replaceAll("([a-z])([A-Z])", "$1 $2");
            // Split number-letter boundaries
            cleanName = cleanName.replaceAll("([0-9])([a-zA-Z])", "$1 $2");
            cleanName = cleanName.replace("_", " ").replace("-", " ");
            // Remove region info in parentheses
            int parenIdx = cleanName.indexOf("(");
            if (parenIdx >= 0) {
                cleanName = cleanName.substring(0, parenIdx).trim();
            }
            // Collapse multiple spaces
            cleanName = cleanName.replaceAll("\\s+", " ").trim();

            String coverPath = getCoverPath(system, fileName);

            json.append("{");
            json.append("\"name\":\"").append(escapeJson(cleanName)).append("\",");
            json.append("\"file\":\"").append(escapeJson(fileName)).append("\",");
            json.append("\"path\":\"").append(escapeJson(f.getAbsolutePath())).append("\",");
            json.append("\"system\":\"").append(escapeJson(system)).append("\",");
            json.append("\"size\":").append(f.length()).append(",");
            json.append("\"cover\":\"").append(escapeJson(coverPath)).append("\"");
            json.append("}");
        }
        return first;
    }

    private String getCoverPath(String system, String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        // Always return the path - let the WebView handle missing images via onerror
        return "covers/" + system + "/" + baseName + ".png";
    }

    @JavascriptInterface
    public void launchGame(String romPath, String system) {
        String core = CoreMap.MAP.get(system);
        if (core == null) return;

        saveRecentGame(romPath, system);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.retroarch",
            "com.retroarch.browser.retroactivity.RetroActivityFuture");
        intent.putExtra("ROM", romPath);
        intent.putExtra("LIBRETRO", CORES_PATH + core);
        intent.putExtra("CONFIGFILE", CONFIG_PATH);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            // RetroArch not available
        }
    }

    @JavascriptInterface
    public void openBluetoothSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            // ignore
        }
    }

    @JavascriptInterface
    public String getControllerInfo() {
        try {
            // Check for actually connected game controllers via InputDevice API
            String controllerName = "";
            boolean connected = false;
            int controllerBattery = -1;

            int[] deviceIds = InputDevice.getDeviceIds();
            for (int id : deviceIds) {
                InputDevice device = InputDevice.getDevice(id);
                if (device == null) continue;
                int sources = device.getSources();
                // Check if it's a gamepad or joystick
                if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                    String name = device.getName();
                    if (name != null && !name.isEmpty()) {
                        controllerName = name;
                        connected = true;
                        // Get battery level via reflection (API 31+)
                        if (Build.VERSION.SDK_INT >= 31) {
                            try {
                                java.lang.reflect.Method getBatteryState = InputDevice.class.getMethod("getBatteryState");
                                Object batteryState = getBatteryState.invoke(device);
                                if (batteryState != null) {
                                    java.lang.reflect.Method getCapacity = batteryState.getClass().getMethod("getCapacity");
                                    float level = (Float) getCapacity.invoke(batteryState);
                                    if (level >= 0) {
                                        controllerBattery = Math.round(level * 100);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        break;
                    }
                }
            }

            // Fallback to Bluetooth paired info if no active controller found
            boolean btEnabled = false;
            if (!connected) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    btEnabled = adapter.isEnabled();
                    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    if (bonded != null) {
                        for (BluetoothDevice dev : bonded) {
                            String devName = dev.getName();
                            if (devName != null && (devName.contains("DualSense") ||
                                devName.contains("Wireless Controller") ||
                                devName.contains("Xbox") ||
                                devName.contains("Pro Controller") ||
                                devName.contains("8BitDo"))) {
                                controllerName = devName;
                                break;
                            }
                        }
                    }
                }
            }

            return "{\"enabled\":" + (connected || btEnabled) +
                   ",\"connected\":" + connected +
                   ",\"paired\":" + (connected || controllerName.length() > 0) +
                   ",\"battery\":" + controllerBattery +
                   ",\"name\":\"" + escapeJson(controllerName) + "\"}";
        } catch (Exception e) {
            return "{\"enabled\":false,\"connected\":false,\"paired\":false,\"battery\":-1,\"name\":\"\"}";
        }
    }

    @JavascriptInterface
    public int getBatteryLevel() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = activity.registerReceiver(null, filter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    @JavascriptInterface
    public boolean isCharging() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = activity.registerReceiver(null, filter);
            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    @JavascriptInterface
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    @JavascriptInterface
    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        }
    }

    @JavascriptInterface
    public void saveRecentGame(String path, String system) {
        try {
            SharedPreferences prefs = activity.getSharedPreferences("retrostation", Context.MODE_PRIVATE);
            String recent = prefs.getString("recent", "");
            String entry = system + "|" + path;
            // Remove if already exists, then prepend
            String[] parts = recent.split("\n");
            StringBuilder sb = new StringBuilder();
            sb.append(entry);
            int count = 1;
            for (int i = 0; i < parts.length && count < 10; i++) {
                if (parts[i].length() > 0 && !parts[i].equals(entry)) {
                    sb.append("\n").append(parts[i]);
                    count++;
                }
            }
            prefs.edit().putString("recent", sb.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
    }

    @JavascriptInterface
    public String getRecentGames() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences("retrostation", Context.MODE_PRIVATE);
            return prefs.getString("recent", "");
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public void launchLemuroid() {
        try {
            Intent intent = activity.getPackageManager()
                .getLaunchIntentForPackage("com.swordfish.lemuroid");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            // Lemuroid not installed
        }
    }

    @JavascriptInterface
    public int getGameCount() {
        File romsDir = new File(ROMS_BASE);
        return countRomsRecursive(romsDir);
    }

    private int countRomsRecursive(File dir) {
        int count = 0;
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countRomsRecursive(f);
            } else {
                String name = f.getName().toLowerCase();
                for (String ext : ROM_EXTENSIONS) {
                    if (name.endsWith(ext)) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    @JavascriptInterface
    public String getSystemCounts() {
        StringBuilder json = new StringBuilder("{");
        File romsDir = new File(ROMS_BASE);
        boolean first = true;
        if (romsDir.exists() && romsDir.isDirectory()) {
            File[] systemDirs = romsDir.listFiles();
            if (systemDirs != null) {
                for (File systemDir : systemDirs) {
                    if (!systemDir.isDirectory()) continue;
                    int count = countRomsRecursive(systemDir);
                    if (count > 0) {
                        if (!first) json.append(",");
                        first = false;
                        json.append("\"").append(escapeJson(systemDir.getName()))
                            .append("\":").append(count);
                    }
                }
            }
        }
        json.append("}");
        return json.toString();
    }

    @JavascriptInterface
    public void downloadGame(final String url, final String system, final String filename) {
        new Thread(() -> {
            try {
                File dir = new File(ROMS_BASE + "/" + system);
                dir.mkdirs();
                File outFile = new File(dir, filename);

                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestProperty("User-Agent", "RetroStation/1.0");
                conn.connect();

                int total = conn.getContentLength();
                java.io.InputStream in = conn.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int read, downloaded = 0;
                while ((read = in.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                    downloaded += read;
                    final int pct = total > 0 ? (int)(downloaded * 100L / total) : -1;
                    final int dl = downloaded;
                    activity.runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "onDownloadProgress(" + pct + ",'" + escapeJson(filename) + "')", null);
                    });
                }
                fos.close();
                in.close();

                activity.runOnUiThread(() -> {
                    webView.evaluateJavascript(
                        "onDownloadComplete('" + escapeJson(filename) + "')", null);
                });
            } catch (Exception e) {
                final String msg = e.getMessage();
                activity.runOnUiThread(() -> {
                    webView.evaluateJavascript(
                        "onDownloadError('" + escapeJson(filename) + "','" + escapeJson(msg) + "')", null);
                });
            }
        }).start();
    }

    @JavascriptInterface
    public int getLemuroidGameId(String romFilename) {
        try {
            File mappingFile = new File(GAME_MAPPING_PATH);
            if (!mappingFile.exists()) return -1;

            java.io.FileInputStream fis = new java.io.FileInputStream(mappingFile);
            byte[] data = new byte[(int) mappingFile.length()];
            fis.read(data);
            fis.close();

            String json = new String(data, "UTF-8");
            // Simple JSON key lookup: find "romFilename": <id>
            String key = "\"" + escapeJson(romFilename) + "\"";
            int idx = json.indexOf(key);
            if (idx < 0) return -1;

            int colonIdx = json.indexOf(":", idx + key.length());
            if (colonIdx < 0) return -1;

            // Extract the numeric value after the colon
            int start = colonIdx + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
                start++;
            }
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            if (end > start) {
                return Integer.parseInt(json.substring(start, end));
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    @JavascriptInterface
    public void launchLemuroidGame(int gameId) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("lemuroid://com.swordfish.lemuroid/play-game/id/" + gameId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY);
            activity.startActivity(intent);
        } catch (Exception e) {
            // Lemuroid not installed or deep link not supported
        }
    }

    @JavascriptInterface
    public void refreshLemuroid() {
        try {
            Intent intent = activity.getPackageManager()
                .getLaunchIntentForPackage("com.swordfish.lemuroid");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                // Return to our launcher after a brief delay to allow rescan
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}
                    Intent backIntent = activity.getPackageManager()
                        .getLaunchIntentForPackage(activity.getPackageName());
                    if (backIntent != null) {
                        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        activity.startActivity(backIntent);
                    }
                }).start();
            }
        } catch (Exception e) {
            // Lemuroid not installed
        }
    }

    @JavascriptInterface
    public String getGameMapping() {
        try {
            File mappingFile = new File(GAME_MAPPING_PATH);
            if (!mappingFile.exists()) return "{}";

            java.io.FileInputStream fis = new java.io.FileInputStream(mappingFile);
            byte[] data = new byte[(int) mappingFile.length()];
            fis.read(data);
            fis.close();

            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void shutdownDevice() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot -p"}).waitFor();
                } catch (Exception e) {
                    try {
                        Runtime.getRuntime().exec(new String[]{"sh", "-c", "reboot -p"}).waitFor();
                    } catch (Exception e2) {
                        android.util.Log.e("RetroStation", "Shutdown failed: " + e2.getMessage());
                    }
                }
            }
        }).start();
    }

    @JavascriptInterface
    public void rebootDevice() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"}).waitFor();
                } catch (Exception e) {
                    try {
                        Runtime.getRuntime().exec(new String[]{"sh", "-c", "reboot"}).waitFor();
                    } catch (Exception e2) {
                        android.util.Log.e("RetroStation", "Reboot failed: " + e2.getMessage());
                    }
                }
            }
        }).start();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
