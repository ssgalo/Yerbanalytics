package com.yerbanalytics.backend.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.mqtt.MqttCommandGateway;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Único punto donde las acciones del motor se convierten en efectos reales.
 *
 * <p>En la <b>Fase 1 (Downlink habilitado)</b>, los efectos son:
 * <ul>
 *   <li>{@code ACTIVAR_VALVULA}: actualiza el campo del sector, registra en historial
 *       y publica el comando MQTT {@code valve ON} al sector.</li>
 *   <li>{@code ACTIVAR_BOMBA}: ídem para la bomba peristáltica ({@code pump ON}).</li>
 *   <li>{@code MOVER_MEDIASOMBRA}: actualiza {@code actuadorShade} del sector con el
 *       porcentaje objetivo extraído del motivo de la acción ({@code [apertura=N]}).</li>
 *   <li>{@code NOOP_INFO}: persiste el motivo de inacción en el historial.</li>
 *   <li>Acciones bloqueantes ({@code ABORT_*}, {@code POSTPONE_RIEGO}): se loguean y
 *       se persiste un registro de inacción; la cadena ya fue detenida por el
 *       {@link RuleOrchestrator}.</li>
 * </ul>
 *
 * <p>El tópico de comando tiene la forma {@code nursery/zone/{zonaId}/sector/{sectorId}/command},
 * alineado con el contrato definido en {@code embebido/comun/contrato.h}.
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    /** Extrae el porcentaje de apertura del motivo de MOVER_MEDIASOMBRA: {@code [apertura=N]}. */
    private static final Pattern APERTURA_PATTERN = Pattern.compile("\\[apertura=(\\d+)\\]");

    /** Extrae el tiempo máximo de riego del motivo de RiegoRule: {@code [tiempo-max-seg=N]}. */
    private static final Pattern TIEMPO_MAX_PATTERN = Pattern.compile("\\[tiempo-max-seg=(\\d+(?:\\.\\d+)?)\\]");

    /** Tópico de comando por sector. Alineado con {@code contrato.h :: contratoTopicComando}. */
    private static final String TOPIC_COMMAND = "nursery/zone/%s/sector/%s/command";

    private final HistorialService historialService;
    private final MqttCommandGateway mqttCommandGateway;
    private final ObjectMapper objectMapper;

    public ActionExecutor(HistorialService historialService,
                          MqttCommandGateway mqttCommandGateway,
                          ObjectMapper objectMapper) {
        this.historialService = historialService;
        this.mqttCommandGateway = mqttCommandGateway;
        this.objectMapper = objectMapper;
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
                        // --- Downlink: enviar orden física al nodo actuador ---
                        int duracionSeg = parseTiempoMax(action.motivo(), 600);
                        publishCommand(ctx, "valve", "ON", Map.of("durationSec", duracionSeg));
                    }
                }

                case ACTIVAR_BOMBA -> {
                    log.info("Sector {}: ACTIVAR_BOMBA — {}", ctx.sector().getId(), action.motivo());
                    ctx.sector().setActuadorPump("Dosificando");
                    if (!"Dosificando".equals(oldPump)) {
                        historialService.registrarInsumo(ctx.sector());
                        // --- Downlink: enviar orden física al nodo actuador ---
                        publishCommand(ctx, "pump", "ON", Map.of());
                    }
                }

                case MOVER_MEDIASOMBRA -> {
                    int apertura = parseApertura(action.motivo(), ctx.sector().getActuadorShade());
                    log.info("Sector {}: MOVER_MEDIASOMBRA → {}% — {}",
                            ctx.sector().getId(), apertura, action.motivo());
                    ctx.sector().setActuadorShade(apertura);
                    // Downlink: enviar posición de mediasombra
                    publishCommand(ctx, "shade", "SET", Map.of("targetPct", apertura));
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

    // -------------------------------------------------------------------------
    // Publicación MQTT
    // -------------------------------------------------------------------------

    /**
     * Construye el JSON del comando según el contrato del embebido y lo publica
     * al tópico del sector a través del {@link MqttCommandGateway}.
     *
     * <p>Formato del payload (alineado con {@code contrato.h}):
     * <pre>
     * {
     *   "commandId": "uuid-v4",
     *   "actuador":  "valve" | "pump" | "shade",
     *   "accion":    "ON" | "OFF" | "SET",
     *   "parametros": { ... }
     * }
     * </pre>
     *
     * <p>Si el gateway lanza una excepción (broker caído, canal lleno, etc.), se
     * registra el error pero NO se frena la ejecución: el estado en base de datos
     * ya fue actualizado y el historial ya fue escrito. El nodo actuará cuando
     * recupere la conexión y el Watchdog proactivo reenvíe la orden.
     */
    private void publishCommand(RuleContext ctx, String actuador, String accion, Map<String, Object> parametros) {
        String zonaId   = ctx.zona() != null ? ctx.zona().getId() : "unknown";
        String sectorId = ctx.sector().getId();
        String topic    = String.format(TOPIC_COMMAND, zonaId, sectorId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId",  UUID.randomUUID().toString());
        payload.put("actuador",   actuador);
        payload.put("accion",     accion);
        payload.put("parametros", parametros);

        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttCommandGateway.send(topic, json);
            log.info("Sector {}: comando MQTT publicado → topic={} payload={}", sectorId, topic, json);
        } catch (JsonProcessingException e) {
            log.error("Sector {}: error serializando comando MQTT — {}", sectorId, e.getMessage());
        } catch (Exception e) {
            // No propagamos: el comando físico se reintentará cuando el motor vuelva a evaluar.
            log.warn("Sector {}: no se pudo publicar el comando MQTT ({}) — {}", sectorId, topic, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Parseo de parámetros del motivo
    // -------------------------------------------------------------------------

    /**
     * Extrae el porcentaje de apertura del motivo de la acción {@code MOVER_MEDIASOMBRA}.
     * Si el motivo no contiene el patrón {@code [apertura=N]}, retorna {@code fallback}.
     */
    private static int parseApertura(String motivo, int fallback) {
        if (motivo == null) return fallback;
        Matcher m = APERTURA_PATTERN.matcher(motivo);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    /**
     * Extrae el tiempo máximo de riego (segundos) del motivo emitido por {@code RiegoRule}.
     * Si el motivo no contiene el patrón {@code [tiempo-max-seg=N]}, retorna {@code fallback}.
     */
    private static int parseTiempoMax(String motivo, int fallback) {
        if (motivo == null) return fallback;
        Matcher m = TIEMPO_MAX_PATTERN.matcher(motivo);
        if (!m.find()) return fallback;
        try {
            return (int) Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
