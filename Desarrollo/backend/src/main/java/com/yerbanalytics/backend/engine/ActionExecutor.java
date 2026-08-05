package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Único punto donde las acciones del motor se convierten en efectos reales.
 *
 * <p>En la <b>Fase 1</b> (refactor puro), los efectos son:
 * <ul>
 *   <li>{@code ACTIVAR_VALVULA}: actualiza el campo del sector y registra en historial.</li>
 *   <li>{@code ACTIVAR_BOMBA}: ídem para la bomba peristáltica.</li>
 *   <li>{@code MOVER_MEDIASOMBRA}: actualiza {@code actuadorShade} del sector con el
 *       porcentaje objetivo extraído del motivo de la acción ({@code [apertura=N]}).</li>
 *   <li>{@code NOOP_INFO}: persiste el motivo de inacción en el historial.</li>
 *   <li>Acciones bloqueantes ({@code ABORT_*}, {@code POSTPONE_RIEGO}): se loguean y
 *       se persiste un registro de inacción; la cadena ya fue detenida por el
 *       {@link RuleOrchestrator}.</li>
 * </ul>
 *
 * <p>En fases futuras este servicio también publicará comandos MQTT al broker
 * (tópico {@code .../command} con QoS 2) y gestionará el In-Flight Lock del sector.
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    /** Extrae el porcentaje de apertura del motivo de MOVER_MEDIASOMBRA: {@code [apertura=N]}. */
    private static final Pattern APERTURA_PATTERN = Pattern.compile("\\[apertura=(\\d+)\\]");

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

                case MOVER_MEDIASOMBRA -> {
                    int apertura = parseApertura(action.motivo(), ctx.sector().getActuadorShade());
                    log.info("Sector {}: MOVER_MEDIASOMBRA → {}% — {}",
                            ctx.sector().getId(), apertura, action.motivo());
                    ctx.sector().setActuadorShade(apertura);
                }

                case NOOP_INFO -> {
                    log.debug("Sector {}: NOOP_INFO — {}", ctx.sector().getId(), action.motivo());
                    // Registro de Inacción: el usuario puede ver por qué el motor no actuó.
                    historialService.registrarInaccion(ctx.sector(), action.ruleName(), action.motivo());
                }

                case ABORT_RIEGO, ABORT_INSUMO, ABORT_ALL, POSTPONE_RIEGO -> {
                    // El corte ya fue aplicado por el RuleOrchestrator.
                    // Se persiste como Registro de Inacción para trazabilidad.
                    log.info("Sector {}: {} — {}", ctx.sector().getId(), action.type(), action.motivo());
                    historialService.registrarInaccion(ctx.sector(), action.ruleName(), action.motivo());
                }

                default -> log.warn("Sector {}: acción desconocida '{}'", ctx.sector().getId(), action.type());
            }
        }
    }

    /**
     * Extrae el porcentaje de apertura del motivo de la acción {@code MOVER_MEDIASOMBRA}.
     * Si el motivo no contiene el patrón {@code [apertura=N]}, retorna {@code fallback}.
     */
    private static int parseApertura(String motivo, int fallback) {
        if (motivo == null) return fallback;
        Matcher m = APERTURA_PATTERN.matcher(motivo);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}
