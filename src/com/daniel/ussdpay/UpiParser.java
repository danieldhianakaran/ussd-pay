package com.daniel.ussdpay;

import android.net.Uri;

/**
 * Parses UPI payment URIs, e.g.
 *   upi://pay?pa=someone@okhdfcbank&pn=Name&am=250.00&cu=INR&tn=Note
 */
public final class UpiParser {

    public static final class Upi {
        public String vpa = "";
        public String name = "";
        public String amount = "";
        public String note = "";
        public String currency = "INR";
        public String raw = "";
        public boolean valid;
    }

    private UpiParser() {
    }

    public static Upi parse(String text) {
        Upi u = new Upi();
        u.raw = text == null ? "" : text;
        if (text == null) {
            return u;
        }
        String t = text.trim();
        if (!t.toLowerCase().startsWith("upi://")) {
            return u;
        }
        try {
            Uri uri = Uri.parse(t);
            String pa = uri.getQueryParameter("pa");
            if (pa == null || pa.isEmpty()) {
                // some QRs put the vpa in "p" or the path
                pa = uri.getQueryParameter("p");
            }
            if (pa != null) {
                u.vpa = pa;
            }
            String pn = uri.getQueryParameter("pn");
            if (pn != null) {
                u.name = pn;
            }
            String am = uri.getQueryParameter("am");
            if (am != null) {
                u.amount = am;
            }
            String tn = uri.getQueryParameter("tn");
            if (tn != null) {
                u.note = tn;
            }
            String cu = uri.getQueryParameter("cu");
            if (cu != null && !cu.isEmpty()) {
                u.currency = cu;
            }
            u.valid = !u.vpa.isEmpty();
        } catch (Exception ignored) {
            u.valid = false;
        }
        return u;
    }
}
