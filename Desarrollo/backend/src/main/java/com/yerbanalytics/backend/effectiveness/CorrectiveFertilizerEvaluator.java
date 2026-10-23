package com.yerbanalytics.backend.effectiveness;

/**
 * E-02 (reglas_v2 §9): el fertilizante correctivo (F-C2) es efectivo si, pasada la espera, el diagnóstico del sector es Sano o
 * la severidad de la clorosis bajó al menos un nivel. Que la clorosis se reemplace por otro daño no cuenta como mejora.
 */
public final class CorrectiveFertilizerEvaluator {

    public Evaluation evaluate(TreatmentBaseline before, DiagnosisView now) {
        if (!now.valid()) {
            return Evaluation.pending("sin diagnóstico vigente: " + now.reason());
        }
        if (now.isHealthy()) {
            return Evaluation.effective(DiagnosisView.HEALTHY, "Sano",
                    "El sector volvió a Sano después del fertilizante correctivo.");
        }
        boolean sameState = before.state().equals(now.state());
        if (sameState && before.severity() != null && now.severity() != null
                && now.severity().ordinal() < before.severity().ordinal()) {
            return Evaluation.effective(describe(now), "mejoró",
                    "La severidad de " + now.state() + " bajó de " + before.severity() + " a " + now.severity() + ".");
        }
        return Evaluation.ineffective(describe(now), sameState ? "igual" : "otro daño",
                sameState
                        ? "La severidad de " + before.state() + " no bajó (antes " + before.severity()
                                + ", ahora " + now.severity() + ")."
                        : "El diagnóstico pasó de " + before.state() + " a " + now.state() + ": la clorosis no se resolvió.");
    }

    private static String describe(DiagnosisView now) {
        return now.severity() == null ? now.state() : now.state() + " " + now.severity();
    }
}
