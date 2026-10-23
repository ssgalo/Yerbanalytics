package com.yerbanalytics.backend.nutrition;

import java.time.Instant;

/**
 * El estado del plan de nutrición de una macro-zona, tal como lo ven N-01 y N-02. Lo arma el {@link NutritionPlanService};
 * las reglas no tocan la base.
 *
 * @param intervalDays        días entre aplicaciones del plan (el de la zona, o el del catálogo si no fijó uno)
 * @param lastPlan            cuándo terminó la última tanda del plan; {@code null} si nunca hubo una
 * @param advancedTo          desde cuándo el plan está vencido por adelantamiento (N-02); {@code null} si no
 * @param batchStart          cuándo arrancó la tanda en curso; {@code null} si no hay una abierta
 * @param pendingInBatch      aplicaciones del plan que todavía esperan una ventana apta
 * @param lastFertilization   la última fertilización en cualquier sector de la zona (del plan o correctiva); {@code null}
 *                            si nunca hubo
 */
public record NutritionPlanFacts(
        int intervalDays,
        Instant lastPlan,
        Instant advancedTo,
        Instant batchStart,
        long pendingInBatch,
        Instant lastFertilization
) {

    /** Hay una tanda abierta: el plan ya arrancó y todavía no terminó. */
    public boolean batchOpen() {
        return batchStart != null;
    }

    /** N-02 ya adelantó el plan y N-01 todavía no lo ejecutó, o está ejecutándose. */
    public boolean pending() {
        return batchOpen() || advancedTo != null;
    }
}
