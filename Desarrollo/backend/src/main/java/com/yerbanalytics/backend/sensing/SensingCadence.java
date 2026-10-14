package com.yerbanalytics.backend.sensing;

/**
 * Single source of truth for the sensing cadence: the pulse interval (minutes, clamped) and the effective
 * stale threshold {@code max(floor, interval + response window)}.
 *
 * <p>The threshold only protects irrigation (a reading older than it is not used to irrigate). Whether a
 * node is out of service follows the missed-pulses count, not this threshold.
 */
public final class SensingCadence {

    public static final int DEFAULT_MINUTES = 240;
    public static final int MIN_MINUTES = 60;
    public static final int MAX_MINUTES = 360;

    private SensingCadence() {
    }

    /** Pulse interval in minutes: {@code null} reads as the default, anything else is clamped to 60–360. */
    public static int intervalMinutes(Integer raw) {
        if (raw == null) {
            return DEFAULT_MINUTES;
        }
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, raw));
    }

    /**
     * Effective stale threshold: the catalog floor, or one pulse interval plus the response window (the time a
     * zone has to answer, retries included) if that is larger.
     */
    public static double staleThresholdSeconds(double floorSeconds, int intervalMinutes, int windowSeconds) {
        return Math.max(floorSeconds, intervalMinutes * 60.0 + windowSeconds);
    }
}
