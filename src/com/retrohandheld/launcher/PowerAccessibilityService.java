package com.retrohandheld.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class PowerAccessibilityService extends AccessibilityService {

    private static PowerAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        android.util.Log.d("RetroStation", "PowerAccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used — this service only exists for performGlobalAction
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled() {
        return instance != null;
    }

    public static void showPowerDialog() {
        if (instance != null) {
            instance.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
    }
}
