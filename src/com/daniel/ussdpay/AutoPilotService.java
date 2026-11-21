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
    private String lastSuccessPrompt = "";
    private String bank = "";
    private int missStreak = 0;

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
            // Nodes can be unreadable for a moment while the dialog is being
            // built, so only conclude the session is over after we have seen
            // the dialog and then lost it repeatedly.
            if (Session.seenDialog() && ++missStreak >= 3) {
                Session.stop();
            }
            return;
        }
        missStreak = 0;

        Session.setSeenDialog();
        String prompt = msg.getText() == null ? "" : msg.getText().toString();
        long now = SystemClock.elapsedRealtime();
        if (prompt.equals(lastPrompt) && now - lastActionAt < 2500) {
            return; // same screen, already handled
        }

        // Record a successful payment exactly once per success screen.
        if (SuccessParser.isSuccess(prompt) && !prompt.equals(lastSuccessPrompt)) {
            lastSuccessPrompt = prompt;
            recordSuccess(prompt);
        }

        String lower = prompt.toLowerCase();
        boolean isMenu = lower.contains("select option") || optionCount(prompt) >= 2;

        String value = null;
        if (isMenu) {
            String menuBank = SuccessParser.bankFromMenu(prompt);
            if (!menuBank.isEmpty()) {
                bank = menuBank;
                prefs.edit().putString("bank", menuBank).apply();
            }
            // A menu screen. Only ever pick "Send Money", the UPI option, or "Exit".
            value = pickMenuOption(prompt);
            if (value == null) {
                log(prompt, "no rule - left to you");
                lastPrompt = prompt;
                lastActionAt = now;
                return; // unrecognised menu: leave it to the user
            }
        } else if (lower.contains("pin")) {
            // Stay armed: the success screen comes *after* the PIN, and we must
            // still see it to record the transaction. We simply never fill it.
            log(prompt, "STOP (PIN must be typed by you)");
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
        log(prompt, "sent \"" + value + "\"");
        toast("Sent: " + value);
        if (lower.contains("exit")) {
            disarm();          // session finished
            Session.stop();
        }
    }

    /** Stores the successful payment and flags it for the receipt dialog. */
    private void recordSuccess(String prompt) {
        SuccessParser.Result r = SuccessParser.parse(prompt);
        String vpa = prefs.getString("vpa", "");
        String amount = r.amount.isEmpty() ? prefs.getString("amount", "") : r.amount;
        String note = prefs.getString("note", "");
        String bankName = bank.isEmpty() ? prefs.getString("bank", "") : bank;
        long id = new TxDb(this).insert(System.currentTimeMillis(), bankName, vpa,
                r.payee, amount, note, r.ref, prompt);
        prefs.edit().putLong("pending_tx", id).apply();
        log(prompt, "SUCCESS recorded #" + id + " ref=" + r.ref);
        toast("Payment recorded: Rs." + amount);
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

    /** Logs the dialog prompt plus the decision taken. */
    private void log(String prompt, String action) {
        String flat = prompt.replace('\n', ' ').trim();
        if (flat.length() > 70) {
            flat = flat.substring(0, 70) + "...";
        }
        UssdLog.add(android.text.format.DateFormat.format(
                "HH:mm:ss", System.currentTimeMillis())
                + "  AUTO  [" + flat + "] -> " + action);
    }

    private void disarm() {
        prefs.edit().putBoolean("armed", false).apply();
    }

    @Override
    public void onInterrupt() {
    }
}
