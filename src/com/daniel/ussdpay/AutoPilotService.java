package com.daniel.ussdpay;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-fills the bank's USSD dialog (window owned by com.android.phone).
 *
 * Rules, applied in order, based on the prompt text:
 *   1. contains "pin"        -> stop, disarm, ask the user to type it
 *   2. contains "upi id"/"vpa" -> paste the scanned VPA
 *   3. contains "amount"     -> paste the amount
 *   4. menu with "N. Send Money" -> send N
 *   5. menu with "N. UPI ..."    -> send N   (labels containing "pin" excluded)
 *   anything else            -> leave untouched for the user
 */
public class AutoPilotService extends AccessibilityService {

    private static final String PHONE_PKG = "com.android.phone";
    private static final String ID_MESSAGE = "android:id/message";
    private static final String ID_INPUT = "com.android.phone:id/input_field";
    private static final String ID_SEND = "android:id/button1";

    private static final Pattern OPTION =
            Pattern.compile("(\\d)\\s*[.)]\\s*([^\\n]{2,40})");

    private SharedPreferences prefs;
    private String lastPrompt = "";
    private long lastActionAt = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences("ussdpay", MODE_PRIVATE);
        toast("USSD autofill ready");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null) {
            prefs = getSharedPreferences("ussdpay", MODE_PRIVATE);
        }
        if (!prefs.getBoolean("armed", false)) {
            return;
        }
        if (event.getPackageName() == null
                || !PHONE_PKG.contentEquals(event.getPackageName())) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        AccessibilityNodeInfo msg = findById(root, ID_MESSAGE);
        AccessibilityNodeInfo input = findById(root, ID_INPUT);
        AccessibilityNodeInfo send = findById(root, ID_SEND);
        if (msg == null || input == null || send == null) {
            return;
        }

        String prompt = msg.getText() == null ? "" : msg.getText().toString();
        long now = SystemClock.elapsedRealtime();
        if (prompt.equals(lastPrompt) && now - lastActionAt < 2500) {
            return; // same screen, already handled
        }

        String lower = prompt.toLowerCase();
        boolean isMenu = lower.contains("select option") || optionCount(prompt) >= 2;

        String value = null;
        if (isMenu) {
            // A menu screen. Only ever pick "Send Money", the UPI option, or "Exit".
            value = pickMenuOption(prompt);
            if (value == null) {
                lastPrompt = prompt;
                lastActionAt = now;
                return; // unrecognised menu: leave it to the user
            }
        } else if (lower.contains("pin")) {
            disarm();
            toast("Enter the UPI PIN yourself");
            lastPrompt = prompt;
            lastActionAt = now;
            return;
        } else if (lower.contains("remark") || lower.contains("note")) {
            // The note typed on the main screen becomes the remark.
            // No note -> "1" skips the remark.
            String note = prefs.getString("note", "");
            value = note.isEmpty() ? "1" : note;
        } else if (lower.contains("upi id") || lower.contains("vpa")) {
            value = prefs.getString("vpa", "");
        } else if (lower.contains("amount")) {
            value = prefs.getString("amount", "");
        }

        if (value == null || value.isEmpty()) {
            lastPrompt = prompt;
            lastActionAt = now;
            return; // not our business, leave it to the user
        }

        String existing = input.getText() == null ? "" : input.getText().toString();
        if (!existing.equals(value)) {
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
        send.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        lastPrompt = prompt;
        lastActionAt = now;
        toast("Sent: " + value);
    }

    /** Picks the digit for "Send Money", else the first UPI option that is not the PIN. */
    private String pickMenuOption(String prompt) {
        Matcher m = OPTION.matcher(prompt);
        while (m.find()) {
            if (m.group(2).toLowerCase().contains("send money")) {
                return m.group(1);
            }
        }
        m.reset();
        while (m.find()) {
            String label = m.group(2).toLowerCase();
            if (label.contains("upi") && !label.contains("pin")) {
                return m.group(1);
            }
        }
        m.reset();
        while (m.find()) {
            if (m.group(2).toLowerCase().contains("exit")) {
                return m.group(1);
            }
        }
        return null;
    }

    private int optionCount(String prompt) {
        Matcher m = OPTION.matcher(prompt);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private AccessibilityNodeInfo findById(AccessibilityNodeInfo root, String viewId) {
        java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0);
        }
        return null;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void disarm() {
        prefs.edit().putBoolean("armed", false).apply();
    }

    @Override
    public void onInterrupt() {
    }
}
