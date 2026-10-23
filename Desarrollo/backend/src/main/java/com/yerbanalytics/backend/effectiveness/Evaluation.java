package com.yerbanalytics.backend.effectiveness;

/**
 * Veredicto de una evaluación de efectividad, con lo que el historial muestra.
 *
 * @param outcome {@code EFFECTIVE} / {@code INEFFECTIVE} cierran el seguimiento; {@code PENDING} espera más datos
 * @param now     valor de "ahora" que muestra el historial (la humedad medida o el estado del diagnóstico)
 * @param delta   variación que muestra el historial (+19, -6, igual…)
 * @param reason  por qué, para el registro
 */
public record Evaluation(Outcome outcome, String now, String delta, String reason) {

    public enum Outcome {
        EFFECTIVE, INEFFECTIVE, PENDING
    }

    public static Evaluation effective(String now, String delta, String reason) {
        return new Evaluation(Outcome.EFFECTIVE, now, delta, reason);
    }

    public static Evaluation ineffective(String now, String delta, String reason) {
        return new Evaluation(Outcome.INEFFECTIVE, now, delta, reason);
    }

    public static Evaluation pending(String reason) {
        return new Evaluation(Outcome.PENDING, "—", "—", reason);
    }

    public boolean isPending() {
        return outcome == Outcome.PENDING;
    }
}
