package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleMediasombra;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.DetalleOutbreakTreatment;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.mediasombra.MediasombraService;
import com.yerbanalytics.backend.supply.ApplicationService;
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
 *   <li>{@code ACTIVAR_BOMBA}: el tratamiento de un sector que decidió una regla de zona (el foco, F-04). Se le pide al
 *       {@link ApplicationService}, que verifica las precondiciones, publica la orden y descuenta el tanque. Sin
 *       {@code P-D1}: los lindantes de un foco no tienen diagnóstico.</li>
 *   <li>{@code MOVER_MEDIASOMBRA}: la orden a la malla de la zona la publica el {@link MediasombraService}.</li>
 *   <li>{@code ABORT_MEDIASOMBRA}: el corte ya lo aplicó el {@link ZoneRuleEngine}; acá sólo se loguea.</li>
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
    private final ApplicationService applications;
    private final MediasombraService mediasombra;

    public ZoneActionExecutor(NutritionPlanService nutritionPlan, HistorialService history,
                              ApplicationService applications, MediasombraService mediasombra) {
        this.nutritionPlan = nutritionPlan;
        this.history = history;
        this.applications = applications;
        this.mediasombra = mediasombra;
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
                case ACTIVAR_BOMBA -> treat(action, ctx);
                case ALERTA -> {
                    if (action.detalle() instanceof DetalleAlerta alert) {
                        history.registrarAlerta(ctx.zone().getId(), ctx.zone().getName(), action.ruleName(), alert,
                                ctx.now().toEpochMilli());
                    } else {
                        log.warn("Zona {}: ALERTA de {} sin detalle: se ignora.", ctx.zone().getId(), action.ruleName());
                    }
                }
                case MOVER_MEDIASOMBRA -> {
                    if (action.detalle() instanceof DetalleMediasombra posicion) {
                        mediasombra.mover(ctx.zone(), posicion.abrir(), action.ruleName(), action.motivo());
                    } else {
                        log.warn("Zona {}: MOVER_MEDIASOMBRA de {} sin posición: no se mueve.",
                                ctx.zone().getId(), action.ruleName());
                    }
                }
                case ABORT_MEDIASOMBRA -> log.debug("Zona {}: ABORT_MEDIASOMBRA — {}", ctx.zone().getId(), action.motivo());
                case NOOP_INFO -> log.debug("Zona {}: NOOP_INFO — {}", ctx.zone().getId(), action.motivo());
                default -> log.warn("Zona {}: una regla de zona emitió {}, que no ejecuta: se ignora.",
                        ctx.zone().getId(), action.type());
            }
        }
    }

    /** Pide la aplicación al sector de la zona que nombra el detalle; un sector ajeno o un detalle faltante no hacen nada. */
    private void treat(RuleAction action, ZoneRuleContext ctx) {
        if (!(action.detalle() instanceof DetalleOutbreakTreatment treatment)) {
            log.warn("Zona {}: ACTIVAR_BOMBA de {} sin detalle: no se aplica nada.", ctx.zone().getId(), action.ruleName());
            return;
        }
        SectorEntity sector = ctx.sectors().stream().filter(s -> s.getId().equals(treatment.sectorId())).findFirst().orElse(null);
        if (sector == null) {
            log.warn("Zona {}: {} pidió tratar a {}, que no es de la zona: no se aplica nada.", ctx.zone().getId(),
                    action.ruleName(), treatment.sectorId());
            return;
        }
        var outcome = applications.request(sector, treatment.supply(), action.ruleName(), false);
        log.info("Sector {}: aplicación de {} pedida por {} → {}{}", sector.getId(), treatment.supply(), action.ruleName(),
                outcome.status(), outcome.reason() != null ? " (" + outcome.reason() + ")" : "");
    }
}
