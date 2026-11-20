package com.daniel.ussdpay;

/**
 * Process-wide event log shared by MainActivity and AutoPilotService.
 * Both live in the same process, so a static buffer is enough.
 */
public final class UssdLog {

    private static final StringBuilder SB = new StringBuilder();
    private static final int MAX_CHARS = 20000;

    private UssdLog() {
    }

    public static synchronized void add(String line) {
        SB.append(line).append('\n');
        if (SB.length() > MAX_CHARS) {
            SB.delete(0, SB.length() - MAX_CHARS);
        }
    }

    public static synchronized String dump() {
        return SB.toString();
    }

    public static synchronized void clear() {
        SB.setLength(0);
    }
}
