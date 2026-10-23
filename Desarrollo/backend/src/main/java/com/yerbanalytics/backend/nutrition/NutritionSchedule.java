package com.yerbanalytics.backend.nutrition;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Cuándo vence el plan de nutrición de una macro-zona (reglas_v2 §8.4). Función pura: la usan N-01 para decidir y el
 * snapshot para mostrar la próxima aplicación, así que no pueden contradecirse.
 *
 * <p>El plan vence cuando pasó el intervalo desde su última aplicación, o antes si N-02 lo adelantó. Una zona sin ninguna
 * aplicación del plan se considera vencida (la v2 no lo dice; propuesta del equipo en {@code add-plan-nutricion} D-5).
 */
public final class NutritionSchedule {

    private NutritionSchedule() {
    }

    /**
     * @param lastPlan   cuándo terminó la última tanda del plan, o {@code null} si nunca hubo una
     * @param advancedTo desde cuándo el plan está vencido por adelantamiento (N-02), o {@code null}
     * @param interval   el intervalo de la zona
     */
    public static boolean isDue(Instant now, Instant lastPlan, Instant advancedTo, Duration interval) {
        if (lastPlan == null) {
            return true;
        }
        return nextDue(lastPlan, advancedTo, interval).map(due -> !now.isBefore(due)).orElse(true);
    }

    /**
     * El instante desde el que el plan está vencido: el más temprano entre el adelantamiento y el fin del intervalo.
     * Vacío si la zona nunca tuvo una aplicación (vence «ya», no hay una fecha).
     */
    public static Optional<Instant> nextDue(Instant lastPlan, Instant advancedTo, Duration interval) {
        if (lastPlan == null) {
            return Optional.empty();
        }
        Instant byInterval = lastPlan.plus(interval);
        return Optional.of(advancedTo != null && advancedTo.isBefore(byInterval) ? advancedTo : byInterval);
    }
}
