package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Controls the shade netting (mediasombra) based on UV radiation and, in a minimal legacy way,
 * on the hardening plan (HU-08).
 *
 * <p>The hardware is <b>binary</b>: the firmware rolls up (opens, 100) for a target &ge; 50 and
 * deploys (closes, 0) below. So the rule only ever asks for 100 or 0; no intermediate openings.
 *
 * <p><b>Priority:</b> 14 — executor, runs after irrigation and supply rules.
 *
 * <p><b>UV peak:</b> if the forecast UV index reaches {@code mediasombra.uv-umbral}, it orders the
 * shade closed (0) <b>every time the peak is active</b>, even if the stored value already says 0:
 * that value is only the last one the backend sent, and the node may have rebooted or been moved by a
 * Demo Expo sequence. The firmware ignores a SET to its current position (it answers
 * {@code sin_cambio} in the ack and keeps {@code act_mediasombra}), so resending is harmless.
 *
 * <p><b>Hardening plan (legacy, to be replaced by add-mediasombra-v2):</b> the stage opening is
 * mapped to binary (&ge; 50 → open, else closed) and ordered only when it differs from the stored
 * binary state.
 *
 * <p><b>Cycle day:</b> uses the sowing date configured in properties
 * ({@code yerbanalytics.nursery.fecha-siembra-iso}, format {@code yyyy-MM-dd}).
 * If not configured, the hardening plan is skipped.
 */
@Component
public class ShadingRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(ShadingRule.class);
    private static final int PRIORITY = 14;
    private static final String NAME = "ShadingRule";
    /** Mismo corte que {@code MEDIASOMBRA_UMBRAL_APERTURA_PCT} del firmware. */
    private static final int BINARY_THRESHOLD = 50;
    private static final int OPEN = 100;
    private static final int CLOSED = 0;

    private final RustificacionEtapaRepository hardeningStageRepository;
    /** Dato del lote, no umbral: única excepción que conserva su {@code @Value} (design D7). */
    private final String sowingDateIso;

    public ShadingRule(
            RustificacionEtapaRepository hardeningStageRepository,
            @Value("${yerbanalytics.nursery.sowing-date-iso:}") String sowingDateIso) {
        this.hardeningStageRepository = hardeningStageRepository;
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
    public String label() {
        return "⛅ Control de mediasombra";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.MEDIASOMBRA;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosMediasombra.UV_UMBRAL);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        int storedBinary = toBinary(ctx.sector().getActuadorShade());

        // --- UV peak: always closes ---
        WeatherForecast forecast = ctx.forecast();
        boolean picoUv = ev.comparar("Índice UV pronosticado", forecast != null ? forecast.uvIndex() : null,
                Operador.GE, ParametrosMediasombra.UV_UMBRAL);
        if (picoUv) {
            double uvThreshold = ev.numero(ParametrosMediasombra.UV_UMBRAL);
            String reason = String.format(java.util.Locale.ROOT,
                    "Mediasombra cerrada por pico de radiación UV (índice %.1f ≥ umbral %.1f).",
                    forecast.uvIndex(), uvThreshold);
            return List.of(shade(CLOSED, reason));
        }

        // --- Hardening plan (legacy) ---
        if (sowingDateIso == null || sowingDateIso.isBlank()) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin pico de UV: la mediasombra no se mueve (fecha de siembra no configurada, sin plan de rustificación)."));
        }

        try {
            LocalDate sowingDate = LocalDate.parse(sowingDateIso);
            long cycleDay = ChronoUnit.DAYS.between(sowingDate, ctx.now().atZone(ZonaHorariaVivero.ZONA).toLocalDate()) + 1;

            List<RustificacionEtapaEntity> stages = hardeningStageRepository.findAllByOrderByOrdenAsc();
            RustificacionEtapaEntity currentStage = stages.stream()
                    .filter(s -> cycleDay >= s.getDiaDesde() && cycleDay <= s.getDiaHasta())
                    .findFirst()
                    .orElse(null);

            if (currentStage == null) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Sin pico de UV: la mediasombra no se mueve (día %d fuera del plan de rustificación).", cycleDay)));
            }

            int targetOpening = toBinary(currentStage.getAperturaPct());
            if (storedBinary == targetOpening) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Mediasombra ya en la posición del plan para el día %d del ciclo (%s).",
                                cycleDay, label(targetOpening))));
            }

            String reason = String.format(
                    "Plan de rustificación: día %d — mediasombra %s.", cycleDay, label(targetOpening));
            return List.of(shade(targetOpening, reason));

        } catch (Exception e) {
            log.warn("ShadingRule: error evaluating hardening plan: {}", e.getMessage());
            return List.of(RuleAction.noopInfo(NAME, "Error al leer plan de rustificación — sin cambio."));
        }
    }

    /**
     * Creates a {@code MOVER_MEDIASOMBRA} action. The target (0 or 100) is embedded in
     * the reason with the parseable prefix {@code [apertura=N]} so that
     * {@code ActionExecutor} can extract it without additional coupling.
     */
    private RuleAction shade(int openingPct, String reason) {
        return RuleAction.of(ActionType.MOVER_MEDIASOMBRA, NAME, "[apertura=" + openingPct + "] " + reason);
    }

    private static int toBinary(int pct) {
        return pct >= BINARY_THRESHOLD ? OPEN : CLOSED;
    }

    private static String label(int binary) {
        return binary == OPEN ? "abierta" : "cerrada";
    }
}
