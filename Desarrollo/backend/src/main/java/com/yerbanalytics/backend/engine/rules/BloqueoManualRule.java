package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.repository.BloqueoManualRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de bloqueo manual de actuación autónoma (HU-19).
 *
 * <p><b>Prioridad:</b> 0 — es la primera regla en evaluarse. Si hay un bloqueo activo,
 * ninguna regla posterior corre: el operario tiene control total.
 *
 * <p><b>Condición:</b> existe al menos un {@code BloqueoManualEntity} activo para
 * el sector evaluado o su zona (bloqueo zonal).
 *
 * <p><b>Acción si hay bloqueo:</b> {@code ABORT_ALL} — detiene toda la cadena de
 * evaluación para este sector en el ciclo actual.
 *
 * <p><b>Acción si no hay bloqueo:</b> {@code NOOP_INFO} — confirmación de que no
 * hay intervención manual activa; la cadena continúa.
 *
 * <p><b>Nota:</b> en el flujo de {@code NurseryService}, el campo
 * {@link RuleContext#bloqueoManualActivo()} ya está poblado antes de llamar al
 * orquestador. Esta regla solo lo lee — no consulta la BD directamente en el ciclo
 * caliente de telemetría, para no agregar latencia.
 */
@Component
public class BloqueoManualRule implements Rule {

    private static final int PRIORITY = 0;
    private static final String NAME = "BloqueoManualRule";

    @SuppressWarnings("unused") // inyectado para disponibilidad futura en modo Watchdog
    private final BloqueoManualRepository bloqueoManualRepository;

    public BloqueoManualRule(BloqueoManualRepository bloqueoManualRepository) {
        this.bloqueoManualRepository = bloqueoManualRepository;
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
        if (ctx.bloqueoManualActivo()) {
            String motivo = String.format(
                    "Bloqueo manual activo sobre el sector %s o su zona. " +
                    "Toda actuación autónoma está suspendida hasta que el operario lo desactive.",
                    ctx.sector().getId());
            return List.of(RuleAction.of(ActionType.ABORT_ALL, NAME, motivo));
        }

        return List.of(RuleAction.noopInfo(NAME,
                "Sin bloqueo manual activo — la cadena de evaluación continúa."));
    }
}
