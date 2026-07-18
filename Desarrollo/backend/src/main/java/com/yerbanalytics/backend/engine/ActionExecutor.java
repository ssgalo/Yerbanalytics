package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Único punto donde las acciones del motor se convierten en efectos reales.
 *
 * <p>En la <b>Fase 1</b> (refactor puro), los efectos son:
 * <ul>
 *   <li>{@code ACTIVAR_VALVULA}: actualiza el campo del sector y registra en historial.</li>
 *   <li>{@code ACTIVAR_BOMBA}: ídem para la bomba peristáltica.</li>
 *   <li>{@code NOOP_INFO}: persiste el motivo de inacción en el historial.</li>
 *   <li>Acciones bloqueantes ({@code ABORT_*}): solo se loguean; la cadena ya fue
 *       detenida por el {@link RuleOrchestrator}.</li>
 * </ul>
 *
 * <p>En fases futuras este servicio también publicará comandos MQTT al broker
 * (tópico {@code .../command} con QoS 2) y gestionará el In-Flight Lock del sector.
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final HistorialService historialService;

    public ActionExecutor(HistorialService historialService) {
        this.historialService = historialService;
    }

    /**
     * Materializa la lista de acciones emitidas por el orquestador.
     *
     * @param actions lista de acciones a ejecutar (puede ser vacía)
     * @param ctx     snapshot inmutable del sector para extraer datos de persistencia
     */
    public void execute(List<RuleAction> actions, RuleContext ctx) {
        String oldValve = ctx.sector().getActuadorValve();
        String oldPump  = ctx.sector().getActuadorPump();

        for (RuleAction action : actions) {
            switch (action.type()) {

                case ACTIVAR_VALVULA -> {
                    log.info("Sector {}: ACTIVAR_VALVULA — {}", ctx.sector().getId(), action.motivo());
                    ctx.sector().setActuadorValve("Regando");
                    // Registrar en historial solo en la transición (no en cada ciclo de telemetría)
                    if (!"Regando".equals(oldValve)) {
                        historialService.registrarRiego(ctx.sector());
                    }
                }

                case ACTIVAR_BOMBA -> {
                    log.info("Sector {}: ACTIVAR_BOMBA — {}", ctx.sector().getId(), action.motivo());
                    ctx.sector().setActuadorPump("Dosificando");
                    if (!"Dosificando".equals(oldPump)) {
                        historialService.registrarInsumo(ctx.sector());
                    }
                }

                case NOOP_INFO -> {
                    log.debug("Sector {}: NOOP_INFO — {}", ctx.sector().getId(), action.motivo());
                    // Registro de Inacción: el usuario puede ver por qué el motor no actuó.
                    historialService.registrarInaccion(ctx.sector(), action.ruleName(), action.motivo());
                }

                case ABORT_RIEGO, ABORT_INSUMO, ABORT_ALL -> {
                    // El corte ya fue aplicado por el RuleOrchestrator.
                    // Aquí solo se loguea para trazabilidad.
                    log.info("Sector {}: {} — {}", ctx.sector().getId(), action.type(), action.motivo());
                }

                default -> log.warn("Sector {}: acción desconocida '{}'", ctx.sector().getId(), action.type());
            }
        }
    }
}
