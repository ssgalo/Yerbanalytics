package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.SecuenciaService;
import com.yerbanalytics.backend.service.SeguimientoComandosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Entrada del ACK de actuadores ({@code nursery/zone/{zona}/sector/{sector}/ack}). Parsea una sola vez y
 * delega a dos consumidores: el seguimiento de comandos (alertas por falla o silencio) y las secuencias de
 * Demo Expo. Un JSON roto se loguea y se descarta, nunca tira abajo el adaptador. Zona y sector salen del
 * tópico, no del payload. Un adaptador propio (ver {@code MqttConfig}) lo alimenta.
 */
@Component
public class AckActuadorReceiver {

    private static final Logger log = LoggerFactory.getLogger(AckActuadorReceiver.class);

    /** Posición de zona y sector en {@code nursery/zone/{zona}/sector/{sector}/ack}. */
    private static final int IDX_ZONA = 2;
    private static final int IDX_SECTOR = 4;

    private final SecuenciaService secuenciaService;
    private final SeguimientoComandosService seguimiento;

    /** Árbol JSON: tolera claves desconocidas, y {@code detalle} llega tal cual (es un objeto). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AckActuadorReceiver(SecuenciaService secuenciaService, SeguimientoComandosService seguimiento) {
        this.secuenciaService = secuenciaService;
        this.seguimiento = seguimiento;
    }

    public void processMessage(Message<?> message) {
        try {
            JsonNode raiz = objectMapper.readTree(String.valueOf(message.getPayload()));
            if (raiz == null || !raiz.isObject()) {
                log.warn("ACK de actuador MQTT que no es un objeto JSON, se descarta");
                return;
            }
            String[] partes = partesDelTopico(message);
            if (partes.length != 6 || !"ack".equals(partes[5])) {
                log.warn("ACK MQTT en un tópico inesperado, se descarta");
                return;
            }
            JsonNode detalle = raiz.get("detalle");
            AckActuador ack = new AckActuador(
                    texto(raiz.get("commandId")),
                    texto(raiz.get("status")),
                    detalle == null || detalle.isNull() ? null : detalle,
                    partes[IDX_ZONA],
                    partes[IDX_SECTOR]);
            entregar(ack);
        } catch (Exception e) {
            log.warn("ACK de actuador MQTT ilegible, se descarta — {}", e.getMessage());
        }
    }

    /** Cada consumidor en su propio try: que uno falle no le quita el ACK al otro. */
    private void entregar(AckActuador ack) {
        try {
            seguimiento.registrarAck(ack);
        } catch (RuntimeException e) {
            log.warn("El seguimiento de comandos no pudo procesar el ACK {} — {}", ack.commandId(), e.getMessage());
        }
        try {
            secuenciaService.registrarAck(ack);
        } catch (RuntimeException e) {
            log.warn("Las secuencias no pudieron procesar el ACK {} — {}", ack.commandId(), e.getMessage());
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
