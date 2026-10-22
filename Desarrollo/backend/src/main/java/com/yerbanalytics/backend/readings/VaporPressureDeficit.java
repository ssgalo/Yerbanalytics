package com.yerbanalytics.backend.readings;

/**
 * Déficit de presión de vapor (reglas_v2 §1.4): cuánto vapor le falta al aire para saturarse, en kPa.
 * Función pura; no sabe de zonas ni de persistencia. Lo calcula la ingesta con cada lectura y las reglas F lo leen
 * de la serie ({@link ReadingHistory}).
 *
 * <pre>
 * es  = 0,6108 · e^(17,27·T / (T + 237,3))
 * DPV = es · (1 − HR/100)
 * </pre>
 */
public final class VaporPressureDeficit {

    /** Temperatura que anula el denominador de la fórmula. */
    private static final double SINGULARITY_C = -237.3;

    private VaporPressureDeficit() {
    }

    /**
     * El DPV sin redondear, o {@code null} si no se puede calcular: falta una entrada, no es un número finito, la
     * humedad cae fuera de 0–100 o la temperatura es la de la singularidad. No inventa un valor.
     *
     * @param tempC temperatura del aire (°C)
     * @param humPct humedad ambiental (%)
     */
    public static Double calculate(Double tempC, Double humPct) {
        if (tempC == null || humPct == null || !Double.isFinite(tempC) || !Double.isFinite(humPct)) {
            return null;
        }
        if (humPct < 0.0 || humPct > 100.0 || tempC == SINGULARITY_C) {
            return null;
        }
        double saturation = 0.6108 * Math.exp(17.27 * tempC / (tempC + 237.3));
        return saturation * (1.0 - humPct / 100.0);
    }

    /** El valor como se persiste: a 0,01 kPa. */
    public static double round(double dpv) {
        return Math.round(dpv * 100.0) / 100.0;
    }
}
