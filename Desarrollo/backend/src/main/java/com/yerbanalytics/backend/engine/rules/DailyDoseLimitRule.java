package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enforces the daily supply dose limit for a sector (HU-07 CA-03).
 *
 * <p><b>Priority:</b> 5 — runs before {@link RiegoRule} and {@link InsumoRule} but after
 * the security and weather rules.
 *
 * <p><b>Condition:</b> the sector has already received a supply dose in the last 24 hours
 * and the number of doses exceeds the maximum configured in
 * {@link ConfiguracionOperativaEntity#getInsumoDosisMax24hMl()}.
 *
 * <p><b>Simplified implementation:</b> the {@code insumoDosisMax24hMl} field represents
 * the maximum daily supply volume. Since the system does not yet track exact volume per dose,
 * this rule counts "Insumo" history events in the last 24 h and blocks if there is at least one
 * (single daily dose). When real volume measurement (flow × time) is integrated, this method
 * can be refined without changing the rule interface.
 *
 * <p><b>Action when limit exceeded:</b> {@code ABORT_INSUMO} (blocking).
 * <p><b>Action when within limit:</b> {@code NOOP_INFO} — chain continues.
 * <p><b>Fail-open:</b> if no configuration is available, returns {@code NOOP_INFO}.
 */
@Component
public class DailyDoseLimitRule implements Rule {

    private static final int PRIORITY = 5;
    private static final String NAME = "DailyDoseLimitRule";
    private static final long MS_PER_24H = 24L * 60 * 60 * 1000;

    private final HistorialRepository historialRepository;

    public DailyDoseLimitRule(HistorialRepository historialRepository) {
        this.historialRepository = historialRepository;
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

        if (config == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin configuración operativa disponible — límite de dosis no verificado."));
        }

        long since = System.currentTimeMillis() - MS_PER_24H;
        long dosesIn24h = historialRepository.countByTipoAndSectorAndPeriod(
                ctx.sector().getId(), "Insumo", since);

        if (dosesIn24h > 0) {
            String reason = String.format(
                    "El sector %s ya recibió %d dosificación(es) de insumo en las últimas 24 h. " +
                    "Límite diario alcanzado (máx. configurado: %.0f ml). " +
                    "Dosificación autónoma bloqueada.",
                    ctx.sector().getId(), dosesIn24h, config.getInsumoDosisMax24hMl());
            return List.of(RuleAction.of(ActionType.ABORT_INSUMO, NAME, reason));
        }

        return List.of(RuleAction.noopInfo(NAME,
                String.format("Sin dosificaciones en las últimas 24 h para el sector %s — " +
                              "límite diario disponible.",
                              ctx.sector().getId())));
    }
}
