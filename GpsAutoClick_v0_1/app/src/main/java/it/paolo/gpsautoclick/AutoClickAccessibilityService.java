package it.paolo.gpsautoclick;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AutoClickAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastClick = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        if (!p.getBoolean("armed", false)) return;
        String targetPackage = p.getString("targetPackage", "");
        String buttonText = p.getString("buttonText", "");
        if (targetPackage.isEmpty() || buttonText.isEmpty()) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !targetPackage.equals(pkg.toString())) return;
        if (System.currentTimeMillis() - lastClick < 1200) return;

        handler.postDelayed(() -> tryClick(buttonText, p), 250);
    }

    private void tryClick(String text, SharedPreferences p) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
        if (matches != null) {
            for (AccessibilityNodeInfo node : matches) {
                if (clickNodeOrParent(node)) {
                    lastClick = System.currentTimeMillis();
                    p.edit().putBoolean("armed", false).apply();
                    return;
                }
            }
        }
        AccessibilityNodeInfo found = findByDescription(root, text.toLowerCase());
        if (found != null && clickNodeOrParent(found)) {
            lastClick = System.currentTimeMillis();
            p.edit().putBoolean("armed", false).apply();
        }
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 6; i++) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo findByDescription(AccessibilityNodeInfo node, String wanted) {
        if (node == null) return null;
        CharSequence d = node.getContentDescription();
        if (d != null && d.toString().toLowerCase().contains(wanted)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByDescription(node.getChild(i), wanted);
            if (r != null) return r;
        }
        return null;
    }

    @Override public void onInterrupt() { }
}
