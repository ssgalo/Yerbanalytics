package com.yerbanalytics.backend.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.MqttCommandGateway;
import com.yerbanalytics.backend.service.SeguimientoComandosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica comandos a los nodos actuadores por el {@link MqttCommandGateway} (el backend sólo publica
 * por acá; el resto del MQTT es consumo de telemetría).
 *
 * <p>Formato del payload (alineado con {@code embebido/comun/contrato.h}):
 * <pre>
 * { "commandId": "uuid-v4", "actuador": "valve" | "pump" | "shade",
 *   "accion": "ON" | "OFF" | "SET", "parametros": { ... } }
 * </pre>
 * en el tópico {@code nursery/zone/{zonaId}/sector/{sectorId}/command}. La mediasombra es de la macro-zona y va por
 * el comando de zona ({@code nursery/zone/{zonaId}/command}, {@link #publicarZona}); su ack vuelve por
 * {@code nursery/zone/{zonaId}/ack}.
 *
 * <p>Cada comando publicado queda en {@link SeguimientoComandosService}, que espera el ack del nodo
 * y alerta si informa una falla o no contesta a tiempo.
 *
 * <p>No traga la falla: devuelve un {@link Resultado} y quien llama decide. El despacho de riego
 * no registra el riego (y deja la solicitud en cola) si no se publicó; la bomba y la mediasombra
 * mantienen su comportamiento de siempre (loguear y seguir).
 */
@Component
public class ComandoActuadorPublisher {

    private static final Logger log = LoggerFactory.getLogger(ComandoActuadorPublisher.class);

    /** Tópico de comando por sector. Alineado con {@code contrato.h :: contratoTopicComando}. */
    private static final String TOPIC_COMMAND = "nursery/zone/%s/sector/%s/command";

    private final MqttCommandGateway gateway;
    private final ObjectMapper objectMapper;
    private final SeguimientoComandosService seguimiento;

    public ComandoActuadorPublisher(MqttCommandGateway gateway, ObjectMapper objectMapper,
                                    SeguimientoComandosService seguimiento) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.seguimiento = seguimiento;
    }

    /**
     * Resultado de un intento de publicación.
     *
     * @param publicado {@code true} si el gateway aceptó el mensaje
     * @param commandId identificador del comando enviado
     * @param error     motivo de la falla; {@code null} si se publicó
     */
    public record Resultado(boolean publicado, String commandId, String error) {
    }

    public Resultado publicar(String zonaId, String sectorId, String actuador, String accion,
                              Map<String, Object> parametros) {
        return enviar(String.format(TOPIC_COMMAND, zonaId, sectorId), zonaId, sectorId, actuador, accion, parametros);
    }

    /**
     * Comando a un actuador de la macro-zona (la mediasombra), por el tópico de comando de zona. El seguimiento lo
     * registra sin sector.
     */
    public Resultado publicarZona(String zonaId, String actuador, String accion, Map<String, Object> parametros) {
        return enviar(String.format(ContratoNodo.TOPIC_COMANDO_ZONA, zonaId), zonaId, null, actuador, accion, parametros);
    }

    private Resultado enviar(String topic, String zonaId, String sectorId, String actuador, String accion,
                             Map<String, Object> parametros) {
        String destino = sectorId != null ? "Sector " + sectorId : "Zona " + zonaId;
        String commandId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", commandId);
        payload.put("actuador", actuador);
        payload.put("accion", accion);
        payload.put("parametros", parametros);

        try {
            String json = objectMapper.writeValueAsString(payload);
            gateway.send(topic, json);
            log.info("{}: comando MQTT publicado → topic={} payload={}", destino, topic, json);
            seguimiento.registrarEnvio(commandId, zonaId, sectorId, actuador, parametros);
            return new Resultado(true, commandId, null);
        } catch (JsonProcessingException e) {
            log.error("{}: error serializando comando MQTT — {}", destino, e.getMessage());
            return new Resultado(false, commandId, "No se pudo serializar el comando: " + e.getMessage());
        } catch (Exception e) {
            log.warn("{}: no se pudo publicar el comando MQTT ({}) — {}", destino, topic, e.getMessage());
            return new Resultado(false, commandId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
