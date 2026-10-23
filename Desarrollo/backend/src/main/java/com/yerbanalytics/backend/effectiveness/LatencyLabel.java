package com.yerbanalytics.backend.effectiveness;

/** La espera de un seguimiento escrita para el historial: minutos hasta una hora, horas hasta dos días, después días. */
public final class LatencyLabel {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private LatencyLabel() {
    }

    public static String of(long ms) {
        if (ms < HOUR_MS) {
            return Math.round(ms / (double) MINUTE_MS) + " min";
        }
        if (ms < 2 * DAY_MS) {
            return Math.round(ms / (double) HOUR_MS) + " h";
        }
        return Math.round(ms / (double) DAY_MS) + " d";
    }
}
