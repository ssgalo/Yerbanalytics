package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Entrada del ACK de actuadores ({@code nursery/zone/{zona}/sector/{sector}/ack}). Sólo deserializa y
 * delega: un JSON roto se loguea y se descarta, nunca tira abajo el adaptador. Zona y sector salen del
 * tópico, no del payload. Un adaptador propio (ver {@code MqttConfig}) lo alimenta.
 */
@Component
public class AckActuadorReceiver {

    private static final Logger log = LoggerFactory.getLogger(AckActuadorReceiver.class);

    /** Posición de zona y sector en {@code nursery/zone/{zona}/sector/{sector}/ack}. */
    private static final int IDX_ZONA = 2;
    private static final int IDX_SECTOR = 4;

    private final SecuenciaService secuenciaService;

    /** Árbol JSON: tolera claves desconocidas, y {@code detalle} llega tal cual (es un objeto). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AckActuadorReceiver(SecuenciaService secuenciaService) {
        this.secuenciaService = secuenciaService;
    }

    public void processMessage(Message<?> message) {
        try {
            JsonNode raiz = objectMapper.readTree(String.valueOf(message.getPayload()));
            if (raiz == null || !raiz.isObject()) {
                log.warn("ACK de actuador MQTT que no es un objeto JSON, se descarta");
                return;
            }
            String[] partes = partesDelTopico(message);
            JsonNode detalle = raiz.get("detalle");
            AckActuador ack = new AckActuador(
                    texto(raiz.get("commandId")),
                    texto(raiz.get("status")),
                    detalle == null || detalle.isNull() ? null : detalle,
                    partes.length > IDX_ZONA ? partes[IDX_ZONA] : null,
                    partes.length > IDX_SECTOR ? partes[IDX_SECTOR] : null);
            secuenciaService.registrarAck(ack);
        } catch (Exception e) {
            log.warn("ACK de actuador MQTT ilegible, se descarta — {}", e.getMessage());
        }
    }

    private static String[] partesDelTopico(Message<?> message) {
        Object topico = message.getHeaders().get("mqtt_receivedTopic");
        return topico == null ? new String[0] : topico.toString().split("/");
    }

    private static String texto(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }
}
