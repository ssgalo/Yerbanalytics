package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Convierte las acciones de las {@link ZoneRule} en efectos reales; es el equivalente por zona del
 * {@code ActionExecutor} de los sectores.
 *
 * <ul>
 *   <li>{@code UPDATE_NUTRITION_PLAN}: la transición del plan la ejecuta el {@link NutritionPlanService}.</li>
 *   <li>{@code ALERTA}: se persiste como evento «Alerta» de la macro-zona. Sin deduplicación por ciclo: una regla de
 *       zona se evalúa una vez por pulso y sólo alerta al cambiar de estado (principio 8 de la v2).</li>
 *   <li>{@code NOOP_INFO}: no se persiste. A diferencia del sector, la zona no tiene fila de historial que hable por ella
 *       (serían 6 por pulso sin dueño): el motivo queda en la traza, que el Inspector muestra.</li>
 * </ul>
 */
@Service
public class ZoneActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ZoneActionExecutor.class);

    private final NutritionPlanService nutritionPlan;
    private final HistorialService history;

    public ZoneActionExecutor(NutritionPlanService nutritionPlan, HistorialService history) {
        this.nutritionPlan = nutritionPlan;
        this.history = history;
    }

    public void execute(List<RuleAction> actions, ZoneRuleContext ctx) {
        for (RuleAction action : actions) {
            switch (action.type()) {
                case UPDATE_NUTRITION_PLAN -> {
                    if (action.detalle() instanceof DetalleNutritionPlan detail) {
                        log.info("Zona {}: {} — {}", ctx.zone().getId(), detail.change(), action.motivo());
                        nutritionPlan.apply(detail.change(), ctx, action.ruleName());
                    } else {
                        log.warn("Zona {}: UPDATE_NUTRITION_PLAN de {} sin detalle: no se hace nada.",
                                ctx.zone().getId(), action.ruleName());
                    }
                }
                case ALERTA -> {
                    if (action.detalle() instanceof DetalleAlerta alert) {
                        history.registrarAlerta(ctx.zone().getId(), ctx.zone().getName(), action.ruleName(), alert,
                                ctx.now().toEpochMilli());
                    } else {
                        log.warn("Zona {}: ALERTA de {} sin detalle: se ignora.", ctx.zone().getId(), action.ruleName());
                    }
                }
                case NOOP_INFO -> log.debug("Zona {}: NOOP_INFO — {}", ctx.zone().getId(), action.motivo());
                default -> log.warn("Zona {}: una regla de zona emitió {}, que no ejecuta: se ignora.",
                        ctx.zone().getId(), action.type());
            }
        }
    }
}
