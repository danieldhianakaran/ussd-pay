package com.daniel.ussdpay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts receipt details from the bank's final USSD screen, e.g.
 *
 *   "Your payment to <payee>, for Rs.1 is successful
 *    (RefId: <ref>). Select option to 1. Save contact 2. Exit"
 */
public final class SuccessParser {

    public static final class Result {
        public boolean success;
        public String payee = "";
        public String amount = "";
        public String ref = "";
        public String raw = "";
    }

    private static final Pattern AMOUNT =
            Pattern.compile("(?:Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYEE =
            Pattern.compile("payment to\\s+([^,\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REF =
            Pattern.compile("(?:RefId|Ref\\.?\\s*Id|Ref|UTR|RRN|Txn\\s*(?:Id|No)?)\\s*[:#]?\\s*([0-9A-Za-z]{5,})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK =
            Pattern.compile("Select Option:\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION_START = Pattern.compile("\\d\\s*[.)]");

    private SuccessParser() {
    }

    public static boolean isSuccess(String prompt) {
        if (prompt == null) {
            return false;
        }
        String p = prompt.toLowerCase();
        return p.contains("successful") || p.contains("is successful")
                || p.contains("payment of rs") || p.contains("has been sent");
    }

    public static Result parse(String prompt) {
        Result r = new Result();
        r.raw = prompt == null ? "" : prompt;
        if (!isSuccess(prompt)) {
            return r;
        }
        r.success = true;
        Matcher m = AMOUNT.matcher(prompt);
        if (m.find()) {
            r.amount = m.group(1).replace(",", "");
        }
        m = PAYEE.matcher(prompt);
        if (m.find()) {
            r.payee = m.group(1).trim();
        }
        m = REF.matcher(prompt);
        if (m.find()) {
            r.ref = m.group(1).trim();
        }
        return r;
    }

    /** Pulls the bank name out of an IOB/NUUP style menu header. */
    public static String bankFromMenu(String prompt) {
        if (prompt == null) {
            return "";
        }
        Matcher m = BANK.matcher(prompt);
        if (!m.find()) {
            return "";
        }
        String rest = m.group(1).trim();
        Matcher opt = OPTION_START.matcher(rest);
        if (opt.find()) {
            rest = rest.substring(0, opt.start());
        }
        return rest.trim();
    }
}
