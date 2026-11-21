package com.daniel.ussdpay;

/**
 * Tracks whether a *99# USSD session is open.
 *
 * "Request in flight" is not the same thing: the first reply ("Welcome to
 * *99#") arrives quickly, but the operator dialog then stays open for the
 * whole menu walk - typically a minute or more. The title indicator should
 * follow the session, not the single request.
 */
public final class Session {

    /** Safety net: never show "live" for longer than this. */
    private static final long MAX_MS = 3 * 60 * 1000L;

    private static volatile long startedAt = 0L;
    private static volatile boolean seenDialog = false;

    private Session() {
    }

    public static void start() {
        startedAt = System.currentTimeMillis();
        seenDialog = false;
    }

    /** Called by the service the first time it sees the operator dialog. */
    public static void setSeenDialog() {
        seenDialog = true;
    }

    public static boolean seenDialog() {
        return seenDialog;
    }

    public static void stop() {
        startedAt = 0L;
        seenDialog = false;
    }

    public static boolean active() {
        long t = startedAt;
        return t != 0L && System.currentTimeMillis() - t < MAX_MS;
    }
}
