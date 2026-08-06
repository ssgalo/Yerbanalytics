package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Controls the shade netting (mediasombra) based on the hardening plan and UV radiation (HU-08).
 *
 * <p><b>Priority:</b> 12 — executor, runs after irrigation and supply rules.
 *
 * <p><b>Primary condition — Hardening plan:</b> if a stage plan exists and the sector's
 * current opening differs from the prescribed opening for the current day of the cycle,
 * emits {@code MOVER_MEDIASOMBRA} with the target percentage.
 *
 * <p><b>Secondary condition — UV peak (overrides plan):</b> if the forecast UV index exceeds
 * the configured threshold ({@code yerbanalytics.engine.uv-umbral}), the opening is reduced
 * to the protective maximum, overriding the plan if necessary.
 *
 * <p><b>Action when no change needed:</b> {@code NOOP_INFO}.
 *
 * <p><b>Cycle day:</b> uses the sowing date configured in properties
 * ({@code yerbanalytics.nursery.fecha-siembra-iso}, format {@code yyyy-MM-dd}).
 * If not configured, the hardening plan is skipped.
 */
@Component
public class ShadingRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(ShadingRule.class);
    private static final int PRIORITY = 12;
    private static final String NAME = "ShadingRule";

    private final RustificacionEtapaRepository hardeningStageRepository;
    private final double uvThreshold;
    private final String sowingDateIso;

    public ShadingRule(
            RustificacionEtapaRepository hardeningStageRepository,
            @Value("${yerbanalytics.engine.uv-threshold:7.0}") double uvThreshold,
            @Value("${yerbanalytics.nursery.sowing-date-iso:}") String sowingDateIso) {
        this.hardeningStageRepository = hardeningStageRepository;
        this.uvThreshold = uvThreshold;
        this.sowingDateIso = sowingDateIso;
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
        ConfiguracionOperativaEntity config = ctx.config();
        double maxOpening = config != null ? config.getMediasombraAperturaMaxPct() : 100.0;
        int currentOpening = ctx.sector().getActuadorShade();

        // --- Secondary condition: UV peak overrides the plan ---
        WeatherForecast forecast = ctx.forecast();
        if (forecast != null && forecast.uvIndex() >= uvThreshold) {
            int protectiveOpening = (int) Math.min(30.0, maxOpening);
            if (currentOpening != protectiveOpening) {
                String reason = String.format(
                        "Pico de radiación UV detectado (índice %.1f ≥ umbral %.1f). " +
                        "Mediasombra reducida a %d%% para proteger plantines.",
                        forecast.uvIndex(), uvThreshold, protectiveOpening);
                return List.of(shade(protectiveOpening, reason));
            }
            return List.of(RuleAction.noopInfo(NAME,
                    String.format("Pico UV activo — mediasombra ya en posición protectora (%d%%).", currentOpening)));
        }

        // --- Primary condition: hardening plan ---
        if (sowingDateIso == null || sowingDateIso.isBlank()) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Fecha de siembra no configurada — plan de rustificación omitido."));
        }

        try {
            LocalDate sowingDate = LocalDate.parse(sowingDateIso);
            long cycleDay = ChronoUnit.DAYS.between(sowingDate, LocalDate.now(ZoneId.systemDefault())) + 1;

            List<RustificacionEtapaEntity> stages = hardeningStageRepository.findAllByOrderByOrdenAsc();
            RustificacionEtapaEntity currentStage = stages.stream()
                    .filter(s -> cycleDay >= s.getDiaDesde() && cycleDay <= s.getDiaHasta())
                    .findFirst()
                    .orElse(null);

            if (currentStage == null) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Día %d fuera del rango del plan de rustificación — sin cambio de mediasombra.", cycleDay)));
            }

            int targetOpening = (int) Math.min(currentStage.getAperturaPct(), maxOpening);
            if (currentOpening == targetOpening) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Mediasombra ya en posición correcta para el día %d del ciclo (%d%%).",
                                cycleDay, currentOpening)));
            }

            String reason = String.format(
                    "Plan de rustificación: día %d — apertura prescrita %d%% (actual: %d%%).",
                    cycleDay, targetOpening, currentOpening);
            return List.of(shade(targetOpening, reason));

        } catch (Exception e) {
            log.warn("ShadingRule: error evaluating hardening plan: {}", e.getMessage());
            return List.of(RuleAction.noopInfo(NAME, "Error al leer plan de rustificación — sin cambio."));
        }
    }

    /**
     * Creates a {@code MOVER_MEDIASOMBRA} action. The target percentage is embedded in
     * the reason with the parseable prefix {@code [apertura=N]} so that
     * {@code ActionExecutor} can extract it without additional coupling.
     */
    private RuleAction shade(int openingPct, String reason) {
        return RuleAction.of(ActionType.MOVER_MEDIASOMBRA, NAME, "[apertura=" + openingPct + "] " + reason);
    }
}
