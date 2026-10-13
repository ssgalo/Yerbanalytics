package com.yerbanalytics.backend.dto;

/**
 * Bandas configurables de una métrica (HU-15). Incluye los metadatos derivados de
 * {@code NurseryConstants.SPECS} (label, unit, dec) para que la UI los muestre sin
 * duplicar la fuente de verdad.
 *
 * @param provisional bandas todavía sin validar con el vivero — corresponde a las métricas
 *                    informativas que llegaron con la sonda de suelo. La UI las señala como
 *                    tales para que nadie las lea como criterio agronómico establecido.
 * @param alertMin    punto de alerta en la franja amarilla baja: debajo (y sin llegar al crítico)
 *                    se emite una alerta WARNING. No cambia el estado. Nulo en un payload = el
 *                    medio de la franja.
 * @param alertMax    ídem en la franja amarilla alta.
 */
public record UmbralMetrica(
        String key,
        String label,
        String unit,
        Integer dec,
        double idealMin,
        double idealMax,
        double warnMin,
        double warnMax,
        double critMin,
        double critMax,
        boolean provisional,
        Double alertMin,
        Double alertMax
) {
    /** Sin puntos de alerta (clientes previos a ellos): valen el medio de cada franja. */
    public UmbralMetrica(String key, String label, String unit, Integer dec,
                         double idealMin, double idealMax, double warnMin, double warnMax,
                         double critMin, double critMax, boolean provisional) {
        this(key, label, unit, dec, idealMin, idealMax, warnMin, warnMax, critMin, critMax, provisional, null, null);
    }

    /** Medio de una franja amarilla redondeado a los decimales de la métrica: el punto de alerta por defecto. */
    public static double puntoMedio(double desde, double hasta, Integer dec) {
        double escala = Math.pow(10, dec != null ? dec : 0);
        return Math.round((desde + hasta) / 2 * escala) / escala;
    }
}
