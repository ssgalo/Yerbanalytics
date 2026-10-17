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
     * Time a zone has to answer a sensing pulse, retries included: the first wait plus one wait per retry.
     * With the factory values (60 s, 2 retries, 1 min) it is 180 s.
     */
    public static double responseWindowSeconds(double responseWaitSeconds, int retries, double retryWaitMinutes) {
        return responseWaitSeconds + retries * retryWaitMinutes * 60.0;
    }

    /**
     * Effective stale threshold: the catalog floor, or one pulse interval plus the response window (the time a
     * zone has to answer, retries included) if that is larger.
     */
    public static double staleThresholdSeconds(double floorSeconds, int intervalMinutes, double windowSeconds) {
        return Math.max(floorSeconds, intervalMinutes * 60.0 + windowSeconds);
    }
}
