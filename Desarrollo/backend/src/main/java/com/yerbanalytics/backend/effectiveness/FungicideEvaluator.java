package com.yerbanalytics.backend.effectiveness;

import java.util.Set;
import java.util.TreeSet;

/**
 * E-03 (reglas_v2 §9): el fitosanitario es efectivo si el daño dejó de avanzar: la severidad del sector no aumentó y ningún
 * lindante nuevo tiene el mismo diagnóstico. Que el daño siga visible no lo hace inefectivo; que se propague, sí.
 */
public final class FungicideEvaluator {

    public Evaluation evaluate(TreatmentBaseline before, DiagnosisView now, Set<String> neighborsNow) {
        if (!now.valid()) {
            return Evaluation.pending("sin diagnóstico vigente: " + now.reason());
        }
        TreeSet<String> spread = new TreeSet<>(neighborsNow);
        spread.removeAll(before.neighbors());
        if (!spread.isEmpty()) {
            return Evaluation.ineffective(describe(now), "se propagó",
                    "El daño llegó a lindantes que antes no lo tenían: " + String.join(", ", spread) + ".");
        }
        boolean grew = before.state().equals(now.state()) && before.severity() != null && now.severity() != null
                && now.severity().ordinal() > before.severity().ordinal();
        if (grew) {
            return Evaluation.ineffective(describe(now), "empeoró",
                    "La severidad aumentó de " + before.severity().label() + " a " + now.severity().label() + ".");
        }
        return Evaluation.effective(describe(now), now.isHealthy() ? "Sano" : "estable",
                now.isHealthy()
                        ? "El sector volvió a Sano después del fitosanitario."
                        : "El daño no avanzó: la severidad no aumentó y no hay lindantes nuevos con el mismo diagnóstico.");
    }

    private static String describe(DiagnosisView now) {
        return now.severity() == null ? now.state() : now.state() + " " + now.severity().label();
    }
}
