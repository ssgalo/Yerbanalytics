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
 * Publica el comando del riel ({@link ContratoRiel}) por el mismo {@link MqttCommandGateway} que
 * usan los actuadores. No traga la falla: devuelve un {@link Resultado} y quien llama decide.
 *
 * <p>Las variantes con {@code commandId} explícito existen para republicar <em>el mismo</em>
 * comando (mismo id): el firmware lo trata como redelivery y no se mueve dos veces.
 */
@Component
public class ComandoRielPublisher {

    private static final Logger log = LoggerFactory.getLogger(ComandoRielPublisher.class);

    private final MqttCommandGateway gateway;
    private final ObjectMapper objectMapper;

    public ComandoRielPublisher(MqttCommandGateway gateway, ObjectMapper objectMapper) {
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

    public Resultado irA(int posicion) {
        return irA(posicion, UUID.randomUUID().toString());
    }

    public Resultado irA(int posicion, String commandId) {
        return publicar(commandId, ContratoRiel.ACCION_IR_A, Map.of("posicion", posicion));
    }

    public Resultado home() {
        return home(UUID.randomUUID().toString());
    }

    public Resultado home(String commandId) {
        return publicar(commandId, ContratoRiel.ACCION_HOME, Map.of());
    }

    private Resultado publicar(String commandId, String accion, Map<String, Object> parametros) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", commandId);
        payload.put("actuador", ContratoRiel.ACTUADOR);
        payload.put("accion", accion);
        payload.put("parametros", parametros);

        try {
            String json = objectMapper.writeValueAsString(payload);
            gateway.send(ContratoRiel.TOPIC_COMANDO, json);
            log.info("Riel: comando MQTT publicado → topic={} payload={}", ContratoRiel.TOPIC_COMANDO, json);
            return new Resultado(true, commandId, null);
        } catch (JsonProcessingException e) {
            log.error("Riel: error serializando el comando — {}", e.getMessage());
            return new Resultado(false, commandId, "No se pudo serializar el comando: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Riel: no se pudo publicar el comando MQTT — {}", e.getMessage());
            return new Resultado(false, commandId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
