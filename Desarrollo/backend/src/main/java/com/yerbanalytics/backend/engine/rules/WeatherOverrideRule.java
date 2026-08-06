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
 * Postpones irrigation when rainfall is imminent (HU-09).
 *
 * <p><b>Priority:</b> 2 — runs after {@link ManualLockRule} and {@link StaleSensorRule},
 * before the irrigation and supply execution rules.
 *
 * <p><b>Condition:</b> the weather forecast ({@link RuleContext#forecast()}) is not
 * {@code null} and the rain probability exceeds the configured threshold.
 *
 * <p><b>Action when rain is imminent:</b> {@code POSTPONE_RIEGO} (blocking) — autonomous
 * irrigation is postponed to avoid combining artificial watering with natural rainfall.
 *
 * <p><b>Action when no forecast available (API down):</b> {@code NOOP_INFO} — degraded
 * mode; the engine continues without weather data (does not block the chain).
 *
 * <p><b>Action when rain is below threshold:</b> {@code NOOP_INFO} — condition verified,
 * chain continues.
 */
@Component
public class WeatherOverrideRule implements Rule {

    private static final int PRIORITY = 2;
    private static final String NAME = "WeatherOverrideRule";

    private final double rainThresholdPct;

    public WeatherOverrideRule(
            @Value("${yerbanalytics.engine.rain-threshold-pct:60.0}") double rainThresholdPct) {
        this.rainThresholdPct = rainThresholdPct;
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

        if (forecast.probLluviaPct() >= rainThresholdPct) {
            String reason = String.format(
                    "Lluvia inminente probable (%.0f%% ≥ umbral %.0f%%). " +
                    "Riego autónomo pospuesto para evitar combinación con lluvia natural.",
                    forecast.probLluviaPct(), rainThresholdPct);
            return List.of(RuleAction.of(ActionType.POSTPONE_RIEGO, NAME, reason));
        }

        return List.of(RuleAction.noopInfo(NAME,
                String.format("Probabilidad de lluvia %.0f%% (< umbral %.0f%%) — " +
                              "no se pospone el riego.",
                              forecast.probLluviaPct(), rainThresholdPct)));
    }
}
