package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.SeguimientoComandosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Entrada de los acks de los actuadores ({@code nursery/zone/{zona}/sector/{sector}/ack}). Sólo saca
 * la zona y el sector del tópico, deserializa y delega: un JSON roto o un tópico inesperado se
 * loguea y se descarta, nunca tira abajo el adaptador.
 */
@Component
public class AckActuadorReceiver {

    private static final Logger log = LoggerFactory.getLogger(AckActuadorReceiver.class);

    private final SeguimientoComandosService seguimiento;

    /** Tolera claves desconocidas: una clave nueva del firmware no debe romper la ingesta. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AckActuadorReceiver(SeguimientoComandosService seguimiento) {
        this.seguimiento = seguimiento;
    }

    public void processMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        try {
            // nursery/zone/{zona}/sector/{sector}/ack
            String[] partes = topic != null ? topic.split("/") : new String[0];
            if (partes.length != 6 || !"ack".equals(partes[5])) {
                log.warn("Ack MQTT en un tópico inesperado ({}), se descarta.", topic);
                return;
            }
            AckActuador ack = objectMapper.readValue(String.valueOf(message.getPayload()), AckActuador.class);
            seguimiento.registrarAck(partes[2], partes[4], ack);
        } catch (Exception e) {
            log.warn("Ack MQTT ilegible ({}), se descarta — {}", topic, e.getMessage());
        }
    }
}
