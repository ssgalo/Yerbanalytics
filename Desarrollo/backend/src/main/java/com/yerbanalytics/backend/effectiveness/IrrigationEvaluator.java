package com.yerbanalytics.backend.effectiveness;

import java.util.Locale;

/**
 * E-01 (reglas_v2 §9): un riego es efectivo si la humedad de sustrato de la macro-zona subió al menos {@code minDelta} puntos
 * entre la lectura previa y la de verificación. El delta lleva signo: un riego tras el cual la humedad baja no es efectivo,
 * aunque el valor absoluto supere el umbral (lo que hacía la evaluación anterior con {@code Math.abs}).
 */
public final class IrrigationEvaluator {

    public Evaluation evaluate(double before, double after, double minDelta) {
        double delta = after - before;
        String shownDelta = (delta >= 0 ? "+" : "") + String.format(Locale.US, "%.0f", delta);
        String shownNow = String.format(Locale.US, "%.0f", after);
        String reason = String.format(Locale.US,
                "Humedad de sustrato %.0f %% → %.0f %% (%s puntos; hacen falta al menos +%.0f).",
                before, after, shownDelta, minDelta);
        return delta >= minDelta
                ? Evaluation.effective(shownNow, shownDelta, reason)
                : Evaluation.ineffective(shownNow, shownDelta, reason);
    }
}
