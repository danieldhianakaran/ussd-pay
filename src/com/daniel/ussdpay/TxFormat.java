package com.daniel.ussdpay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formats transactions for the receipt dialog and the share sheet. */
public final class TxFormat {

    private static final SimpleDateFormat WHEN =
            new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

    private TxFormat() {
    }

    public static String when(long ts) {
        return WHEN.format(new Date(ts));
    }

    /** Text put in the share sheet - what you show a vendor. */
    public static String receipt(long ts, String bank, String vpa, String payee,
                                 String amount, String ref) {
        StringBuilder sb = new StringBuilder();
        sb.append("Payment successful\n");
        sb.append("Rs.").append(amount == null || amount.isEmpty() ? "?" : amount)
                .append(" to ").append(vpa);
        if (payee != null && !payee.isEmpty()) {
            sb.append(" (").append(payee).append(')');
        }
        sb.append('\n');
        if (bank != null && !bank.isEmpty()) {
            sb.append("Bank: ").append(bank).append('\n');
        }
        if (ref != null && !ref.isEmpty()) {
            sb.append("Ref: ").append(ref).append('\n');
        }
        sb.append("Date: ").append(when(ts)).append('\n');
        sb.append("via USSD Pay");
        return sb.toString();
    }

    /** One-line summary for the history list. */
    public static String row(long ts, String amount, String vpa, String ref) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rs.").append(amount == null || amount.isEmpty() ? "?" : amount);
        sb.append("   ").append(when(ts)).append('\n');
        sb.append(vpa == null ? "" : vpa);
        if (ref != null && !ref.isEmpty()) {
            sb.append("   Ref ").append(ref);
        }
        return sb.toString();
    }
}
