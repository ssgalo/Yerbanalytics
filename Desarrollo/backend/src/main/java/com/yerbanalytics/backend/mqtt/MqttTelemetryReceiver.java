package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.NurseryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttTelemetryReceiver {

    private final NurseryService nurseryService;

    /**
     * Deserializa tolerando claves desconocidas: el firmware publica métricas que la
     * plataforma no modela (ver {@link ContratoNodo}) y una clave nueva del lado del nodo no
     * debe tirar abajo la ingesta de todo el paquete.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Compuerta por tópico: {@link TelemetryAdmission#ALL} salvo que el modo demo aporte la suya. */
    private final TelemetryAdmission admission;

    @Autowired
    public MqttTelemetryReceiver(NurseryService nurseryService, ObjectProvider<TelemetryAdmission> admission) {
        this(nurseryService, admission.getIfAvailable(() -> TelemetryAdmission.ALL));
    }

    public MqttTelemetryReceiver(NurseryService nurseryService) {
        this(nurseryService, TelemetryAdmission.ALL);
    }

    MqttTelemetryReceiver(NurseryService nurseryService, TelemetryAdmission admission) {
        this.nurseryService = nurseryService;
        this.admission = admission;
    }

    public void processMessage(Message<?> message) {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            if (!admission.admits(topic)) {
                return;
            }
            String payload = (String) message.getPayload();
            MqttTelemetryPayload telemetry = objectMapper.readValue(payload, MqttTelemetryPayload.class);
            // Zona del tópico (nursery/zone/{zoneId}/telemetry, o su variante de demo)
            String zoneId = TelemetryAdmission.zoneId(topic);
            if (zoneId == null) {
                // Sin tópico de zona no hay a quién atribuir la lectura: se descarta.
                return;
            }
            nurseryService.updateTelemetry(zoneId, telemetry);
        } catch (Exception e) {
            // Log error (using System.err for simplicity)
            System.err.println("Failed to process MQTT telemetry: " + e.getMessage());
        }
    }
}
