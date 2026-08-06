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
 * Regla de límite de dosificación en 24 horas (HU-07 CA-03).
 *
 * <p><b>Prioridad:</b> 5 — corre antes de {@link RiegoRule} e {@link InsumoRule} pero
 * después de las reglas de seguridad y clima.
 *
 * <p><b>Condición:</b> el sector ya recibió una dosificación de insumo en las últimas
 * 24 horas y el número de dosis supera el máximo configurado en
 * {@link ConfiguracionOperativaEntity#getInsumoDosisMax24hMl()}.
 *
 * <p><b>Implementación simplificada:</b> el campo {@code insumoDosisMax24hMl} en la entidad
 * de configuración representa el volumen máximo de insumo por día. Como el sistema actual
 * no trackea el volumen exacto por dosis, esta regla cuenta el número de eventos de tipo
 * "Insumo" en las últimas 24 h y bloquea si hay al menos uno (dosis diaria única).
 * Cuando se integre la medición real de volumen (caudal × tiempo), este método se puede
 * refinar sin cambiar la interfaz de la regla.
 *
 * <p><b>Acción si se superó el límite:</b> {@code ABORT_INSUMO} (bloqueante de dosificación).
 * <p><b>Acción si no se superó:</b> {@code NOOP_INFO} — la cadena continúa.
 */
@Component
public class DosisLimiteRule implements Rule {

    private static final int PRIORITY = 5;
    private static final String NAME = "DosisLimiteRule";
    private static final long MS_EN_24H = 24L * 60 * 60 * 1000;

    private final HistorialRepository historialRepository;

    public DosisLimiteRule(HistorialRepository historialRepository) {
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

        // Sin configuración disponible: no bloquear (fail-open).
        if (config == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin configuración operativa disponible — límite de dosis no verificado."));
        }

        long desde = System.currentTimeMillis() - MS_EN_24H;
        long dosisEn24h = historialRepository.countByTipoAndSectorAndPeriod(
                ctx.sector().getId(), "Insumo", desde);

        if (dosisEn24h > 0) {
            String motivo = String.format(
                    "El sector %s ya recibió %d dosificación(es) de insumo en las últimas 24 h. " +
                    "Límite diario alcanzado (máx. configurado: %.0f ml). " +
                    "Dosificación autónoma bloqueada.",
                    ctx.sector().getId(), dosisEn24h, config.getInsumoDosisMax24hMl());
            return List.of(RuleAction.of(ActionType.ABORT_INSUMO, NAME, motivo));
        }

        return List.of(RuleAction.noopInfo(NAME,
                String.format("Sin dosificaciones en las últimas 24 h para el sector %s — " +
                              "límite diario disponible.",
                              ctx.sector().getId())));
    }
}
