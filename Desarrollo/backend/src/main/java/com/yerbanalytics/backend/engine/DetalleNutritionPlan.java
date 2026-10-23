package com.yerbanalytics.backend.engine;

/**
 * Qué decidió una regla de macro-zona sobre el plan de nutrición. La regla decide la transición; quien la ejecuta es el
 * {@code NutritionPlanService}.
 *
 * @param change la transición del plan
 */
public record DetalleNutritionPlan(Change change) implements DetalleAccion {

    public enum Change {
        /** Abre la tanda: pide fertilizante en los sectores de la zona y la deja abierta hasta que terminen. */
        START,
        /** Vence el plan ahora, sin esperar el intervalo (N-02). */
        ADVANCE,
        /** Cierra la tanda: ya no queda ninguna aplicación del plan esperando. */
        COMPLETE
    }
}
