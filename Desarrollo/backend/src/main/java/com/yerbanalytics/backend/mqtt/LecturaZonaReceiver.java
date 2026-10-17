package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Segunda suscripción a la telemetría de las zonas, <strong>sólo</strong> para que las secuencias de
 * lectura se enteren de la respuesta a su pedido. Sella la hora de recepción con el reloj del vivero (el
 * {@code timestamp} del payload no sirve: un ESP32 sin NTP manda segundos desde el arranque) y avisa a
 * {@link SecuenciaService}.
 *
 * <p>No toca {@code NurseryService}: la ingesta que alimenta al motor de reglas es la de siempre
 * ({@link MqttTelemetryReceiver}) y queda byte a byte igual. Costo: el broker entrega cada lectura dos
 * veces al backend. Un JSON roto se loguea y se descarta.
 */
@Component
public class LecturaZonaReceiver {

    private static final Logger log = LoggerFactory.getLogger(LecturaZonaReceiver.class);

    private final SecuenciaService secuenciaService;
    private final Clock reloj;

    /** Tolera claves desconocidas, como la ingesta: una clave nueva del firmware no debe romperla. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Compuerta por tópico: {@link TelemetryAdmission#ALL} salvo que el modo demo aporte la suya. */
    private final TelemetryAdmission admission;

    @Autowired
    public LecturaZonaReceiver(SecuenciaService secuenciaService, Clock relojVivero,
                               ObjectProvider<TelemetryAdmission> admission) {
        this(secuenciaService, relojVivero, admission.getIfAvailable(() -> TelemetryAdmission.ALL));
    }

    public LecturaZonaReceiver(SecuenciaService secuenciaService, Clock relojVivero) {
        this(secuenciaService, relojVivero, TelemetryAdmission.ALL);
    }

    LecturaZonaReceiver(SecuenciaService secuenciaService, Clock relojVivero, TelemetryAdmission admission) {
        this.secuenciaService = secuenciaService;
        this.reloj = relojVivero;
        this.admission = admission;
    }

    public void processMessage(Message<?> message) {
        try {
            Object topico = message.getHeaders().get("mqtt_receivedTopic");
            String zona = TelemetryAdmission.zoneId(topico == null ? null : topico.toString());
            if (zona == null) {
                log.warn("Lectura MQTT sin tópico reconocible, se descarta");
                return;
            }
            if (!admission.admits(topico.toString())) {
                return;
            }
            MqttTelemetryPayload payload =
                    objectMapper.readValue(String.valueOf(message.getPayload()), MqttTelemetryPayload.class);
            secuenciaService.registrarTelemetria(zona, payload, reloj.millis());
        } catch (Exception e) {
            log.warn("Lectura MQTT ilegible para las secuencias, se descarta — {}", e.getMessage());
        }
    }
}
