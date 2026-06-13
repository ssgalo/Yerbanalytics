package com.yerbanalytics.backend.service.mock;

/**
 * RNG determinístico mulberry32 — port exacto de {@code frontend/src/lib/rng.ts}.
 * El orden de llamadas a {@link #next()} define la salida; no reordenar.
 */
public final class Mulberry32Rng {

    private int state;

    public Mulberry32Rng(int seed) {
        this.state = seed;
    }

    public double next() {
        state = state + 0x6d2b79f5;
        int t = imul(state ^ (state >>> 15), 1 | state);
        t = imul(t ^ (t >>> 7), 61 | t) ^ t;
        return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
    }

    private static int imul(int a, int b) {
        return (int) ((long) a * (long) b);
    }
}
