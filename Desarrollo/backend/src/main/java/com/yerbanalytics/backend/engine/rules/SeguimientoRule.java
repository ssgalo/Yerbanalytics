package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de seguimiento post-acción (R4/HU-12).
 *
 * <p><b>Prioridad:</b> 20 — la última en evaluarse. A diferencia de las demás reglas,
 * no decide si actuar: su función es verificar la efectividad de las acciones previas
 * del sector y actualizar el historial.
 *
 * <p><b>Diseño:</b> esta regla es un <em>delegador</em> — no duplica la lógica de
 * evaluación de efectividad que ya existe en {@link HistorialService#evaluarSeguimiento()}.
 * Simplemente invoca ese método en el contexto del motor de reglas para centralizar
 * la evaluación post-acción. La evaluación global (todos los sectores) sigue
 * disparándose desde el {@code @Scheduled} del {@link HistorialService}.
 *
 * <p><b>Nota sobre el patrón:</b> el {@code @Scheduled} en {@link HistorialService} NO se
 * elimina — es el Watchdog que cubre el caso de sectores sin telemetría reciente.
 * Esta regla complementa ese scheduler evaluando en cada ciclo reactivo los eventos
 * pendientes del sector específico que recibió telemetría.
 *
 * <p><b>Acción:</b> siempre retorna {@code NOOP_INFO} — el seguimiento es una tarea
 * interna de auditoría, no produce efectos sobre actuadores.
 */
@Component
public class SeguimientoRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoRule.class);
    private static final int PRIORITY = 20;
    private static final String NAME = "SeguimientoRule";

    private final HistorialService historialService;

    public SeguimientoRule(HistorialService historialService) {
        this.historialService = historialService;
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
        try {
            // Delega al scheduler existente en HistorialService.
            // En el futuro se puede refinar para evaluar solo los eventos del sector específico.
            historialService.evaluarSeguimiento();
            log.debug("SeguimientoRule: evaluación de seguimiento ejecutada para sector {}.",
                    ctx.sector().getId());
        } catch (Exception e) {
            log.warn("SeguimientoRule: error durante la evaluación de seguimiento: {}", e.getMessage());
        }

        return List.of(RuleAction.noopInfo(NAME,
                "Evaluación de seguimiento post-acción completada."));
    }
}
