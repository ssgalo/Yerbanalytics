package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.HardwareFaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Entrada de los errores del nodo ({@code nursery/zone/{zona}/errores}, S-05). Parsea el payload del contrato, saca la
 * zona del tópico (no del payload) y entrega el error a {@link HardwareFaultService}. Un tipo que el contrato no define
 * ({@link NodeErrorType}), un JSON roto o un tópico inesperado se loguean y se descartan: nunca cambian un estado ni
 * tiran abajo el adaptador. Un adaptador propio (ver {@code MqttConfig}) lo alimenta.
 *
 * <p>El {@code timestamp} del payload no se usa: el reloj del nodo puede estar sin NTP y la falla se fecha con la
 * hora de recepción.
 */
@Component
public class NodeErrorReceiver {

    private static final Logger log = LoggerFactory.getLogger(NodeErrorReceiver.class);

    /** Posición de la zona en {@code nursery/zone/{zona}/errores}. */
    private static final int IDX_ZONA = 2;

    private final HardwareFaultService faults;

    /** Árbol JSON: tolera claves desconocidas, y {@code valor} llega como número o como texto según el tipo. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NodeErrorReceiver(HardwareFaultService faults) {
        this.faults = faults;
    }

    public void processMessage(Message<?> message) {
        try {
            String[] parts = topicParts(message);
            if (parts.length != 4 || !"errores".equals(parts[3])) {
                log.warn("Error de nodo MQTT en un tópico inesperado, se descarta");
                return;
            }
            JsonNode root = objectMapper.readTree(String.valueOf(message.getPayload()));
            if (root == null || !root.isObject()) {
                log.warn("Error de nodo MQTT que no es un objeto JSON, se descarta");
                return;
            }
            Optional<NodeErrorType> type = NodeErrorType.fromWire(text(root.get("tipo")));
            if (type.isEmpty()) {
                log.warn("Zona {}: error de nodo con un tipo que el contrato no define ('{}'), se descarta.",
                        parts[IDX_ZONA], text(root.get("tipo")));
                return;
            }
            faults.report(new NodeError(parts[IDX_ZONA], text(root.get("nodo")), text(root.get("sector")),
                    type.get(), text(root.get("valor"))));
        } catch (Exception e) {
            log.warn("Error de nodo MQTT ilegible o no procesable, se descarta — {}", e.getMessage());
        }
    }

    private static String[] topicParts(Message<?> message) {
        Object topic = message.getHeaders().get("mqtt_receivedTopic");
        return topic == null ? new String[0] : topic.toString().split("/");
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }
}
