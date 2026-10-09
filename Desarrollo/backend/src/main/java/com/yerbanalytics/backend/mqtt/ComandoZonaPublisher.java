package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica el comando de zona "leer ahora" ({@link ContratoNodo#TOPIC_COMANDO_ZONA}) por el mismo
 * {@link MqttCommandGateway} que usan los actuadores y el riel. No traga la falla: devuelve un
 * {@link Resultado} y quien llama decide.
 *
 * <p>Payload: {@code {"commandId","accion":"LEER_AHORA","parametros":{}}}. Sin {@code actuador}: no es
 * un actuador. La respuesta no es un ACK sino la telemetría de siempre de esa zona.
 */
@Component
public class ComandoZonaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ComandoZonaPublisher.class);

    private final MqttCommandGateway gateway;
    private final ObjectMapper objectMapper;

    public ComandoZonaPublisher(MqttCommandGateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    /**
     * @param publicado {@code true} si el gateway aceptó el mensaje
     * @param commandId identificador del comando enviado
     * @param error     motivo de la falla; {@code null} si se publicó
     */
    public record Resultado(boolean publicado, String commandId, String error) {
    }

    /** Pide al nodo testigo de {@code zonaId} que lea sus sensores ahora. */
    public Resultado leerAhora(String zonaId) {
        String topic = String.format(ContratoNodo.TOPIC_COMANDO_ZONA, zonaId);
        String commandId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", commandId);
        payload.put("accion", ContratoNodo.ACCION_LEER_AHORA);
        payload.put("parametros", Map.of());

        try {
            String json = objectMapper.writeValueAsString(payload);
            gateway.send(topic, json);
            log.info("Zona {}: comando MQTT publicado → topic={} payload={}", zonaId, topic, json);
            return new Resultado(true, commandId, null);
        } catch (JsonProcessingException e) {
            log.error("Zona {}: error serializando el comando — {}", zonaId, e.getMessage());
            return new Resultado(false, commandId, "No se pudo serializar el comando: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Zona {}: no se pudo publicar el comando MQTT ({}) — {}", zonaId, topic, e.getMessage());
            return new Resultado(false, commandId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
