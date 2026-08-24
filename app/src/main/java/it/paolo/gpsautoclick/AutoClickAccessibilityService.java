package it.paolo.gpsautoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoClickAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int MAX_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MS = 650;
    private int attempt = 0;
    private String currentTargetPackage = "";
    private String currentButtonText = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        if (!p.getBoolean("armed", false)) return;

        String targetPackage = p.getString("targetPackage", "");
        String buttonText = p.getString("buttonText", "");
        if (targetPackage.isEmpty() || buttonText.isEmpty()) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !targetPackage.equals(pkg.toString())) return;

        if (!targetPackage.equals(currentTargetPackage) || !buttonText.equals(currentButtonText)) {
            currentTargetPackage = targetPackage;
            currentButtonText = buttonText;
            attempt = 0;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> tryClickLoop(p), 700);
        } else if (attempt == 0) {
            handler.postDelayed(() -> tryClickLoop(p), 500);
        }
    }

    private void tryClickLoop(SharedPreferences p) {
        if (!p.getBoolean("armed", false)) return;
        if (attempt >= MAX_ATTEMPTS) {
            p.edit().putString("lastAutoStatus", "Pulsante non trovato dopo diversi tentativi").apply();
            attempt = 0;
            return;
        }
        attempt++;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo node = findBestMatch(root, currentButtonText);
            if (node != null) {
                if (clickNodeOrParent(node)) {
                    markSuccess(p, "Tasto premuto con Accessibility");
                    return;
                }
                if (tapNodeCenter(node, p)) {
                    return;
                }
            }
        }

        p.edit().putString("lastAutoStatus", "Tentativo " + attempt + "/" + MAX_ATTEMPTS + ": cerco \"" + currentButtonText + "\"").apply();
        handler.postDelayed(() -> tryClickLoop(p), RETRY_DELAY_MS);
    }

    private AccessibilityNodeInfo findBestMatch(AccessibilityNodeInfo root, String wantedText) {
        String wanted = normalize(wantedText);

        List<AccessibilityNodeInfo> exactMatches = root.findAccessibilityNodeInfosByText(wantedText);
        if (exactMatches != null) {
            for (AccessibilityNodeInfo node : exactMatches) {
                if (node != null && isTextMatch(node, wanted)) return node;
            }
        }

        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectNodes(root, all);
        AccessibilityNodeInfo partial = null;
        for (AccessibilityNodeInfo node : all) {
            if (node == null) continue;
            String text = normalize(node.getText());
            String desc = normalize(node.getContentDescription());
            if (wanted.equals(text) || wanted.equals(desc)) return node;
            if (partial == null && ((!text.isEmpty() && text.contains(wanted)) || (!desc.isEmpty() && desc.contains(wanted)))) {
                partial = node;
            }
        }
        return partial;
    }

    private boolean isTextMatch(AccessibilityNodeInfo node, String wanted) {
        String text = normalize(node.getText());
        String desc = normalize(node.getContentDescription());
        return wanted.equals(text) || wanted.equals(desc) || text.contains(wanted) || desc.contains(wanted);
    }

    private String normalize(CharSequence value) {
        if (value == null) return "";
        return value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private void collectNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), out);
        }
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 8; i++) {
            if (current.isEnabled() && current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            current = current.getParent();
        }
        return node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean tapNodeCenter(AccessibilityNodeInfo node, SharedPreferences p) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;

        float x = bounds.exactCenterX();
        float y = bounds.exactCenterY();
        if (x < 0 || y < 0) return false;

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();

        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                markSuccess(p, "Tasto premuto con tap simulato");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                handler.postDelayed(() -> tryClickLoop(p), RETRY_DELAY_MS);
            }
        }, handler);
    }

    private void markSuccess(SharedPreferences p, String message) {
        p.edit()
                .putBoolean("armed", false)
                .putString("lastAutoStatus", message)
                .apply();
        attempt = 0;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }
}
