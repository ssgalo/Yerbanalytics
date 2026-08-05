package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de postergación de riego por pronóstico de lluvia inminente (HU-09).
 *
 * <p><b>Prioridad:</b> 2 (corre después de {@link BloqueoManualRule} y {@link StaleSensorRule},
 * antes de las reglas ejecutoras de riego e insumo).
 *
 * <p><b>Condición:</b> el pronóstico climático ({@link RuleContext#forecast()}) no es
 * {@code null} y la probabilidad de lluvia supera el umbral configurado.
 *
 * <p><b>Acción si lluvia inminente:</b> {@code POSTPONE_RIEGO} (bloqueante) — el riego
 * autónomo se pospone para no combinar riego artificial con lluvia natural.
 *
 * <p><b>Acción si no hay pronóstico (API caída):</b> {@code NOOP_INFO} de degradación
 * — el motor continúa sin clima (no bloquea la cadena).
 *
 * <p><b>Acción si lluvia bajo umbral:</b> {@code NOOP_INFO} — condición verificada,
 * la cadena continúa.
 */
@Component
public class ClimaOverrideRule implements Rule {

    private static final int PRIORITY = 2;
    private static final String NAME = "ClimaOverrideRule";

    private final double lluviaUmbralPct;

    public ClimaOverrideRule(
            @Value("${yerbanalytics.engine.lluvia-umbral-pct:60.0}") double lluviaUmbralPct) {
        this.lluviaUmbralPct = lluviaUmbralPct;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx) {
        WeatherForecast forecast = ctx.forecast();

        if (forecast == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Pronóstico climático no disponible (API degradada) — " +
                    "el motor evalúa solo con sensores."));
        }

        if (forecast.probLluviaPct() >= lluviaUmbralPct) {
            String motivo = String.format(
                    "Lluvia inminente probable (%.0f%% ≥ umbral %.0f%%). " +
                    "Riego autónomo pospuesto para evitar combinación con lluvia natural.",
                    forecast.probLluviaPct(), lluviaUmbralPct);
            return List.of(RuleAction.of(ActionType.POSTPONE_RIEGO, NAME, motivo));
        }

        return List.of(RuleAction.noopInfo(NAME,
                String.format("Probabilidad de lluvia %.0f%% (< umbral %.0f%%) — " +
                              "no se pospone el riego.",
                              forecast.probLluviaPct(), lluviaUmbralPct)));
    }
}
