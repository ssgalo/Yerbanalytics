package com.yerbanalytics.backend.sensing;

/**
 * Single source of truth for the backend-driven sensing cadence: the pull interval X (clamped)
 * and the effective stale threshold {@code max(floor, 3 * X)}.
 */
public final class SensingCadence {

    public static final int DEFAULT_SECONDS = 60;
    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 3600;

    /** A reading is stale after this many missed pull intervals. */
    private static final int STALE_INTERVALS = 3;

    private SensingCadence() {
    }

    /** Pull interval in seconds: {@code null} reads as the default, anything else is clamped to the range. */
    public static int pullSeconds(Integer raw) {
        if (raw == null) {
            return DEFAULT_SECONDS;
        }
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, raw));
    }

    /** Effective stale threshold: the catalog floor, or three pull intervals if that is larger. */
    public static double staleThresholdSeconds(double floorSeconds, int pullSeconds) {
        return Math.max(floorSeconds, (double) STALE_INTERVALS * pullSeconds);
    }
}
