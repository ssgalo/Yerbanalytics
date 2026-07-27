package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.NurseryService;
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

    @Autowired
    public MqttTelemetryReceiver(NurseryService nurseryService) {
        this.nurseryService = nurseryService;
    }

    public void processMessage(Message<?> message) {
        try {
            String payload = (String) message.getPayload();
            MqttTelemetryPayload telemetry = objectMapper.readValue(payload, MqttTelemetryPayload.class);
            // Extract zoneId from topic (format: nursery/zone/{zoneId}/telemetry)
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String[] parts = topic.split("/");
            String zoneId = parts.length >= 4 ? parts[2] : "unknown";
            nurseryService.updateTelemetry(zoneId, telemetry);
        } catch (Exception e) {
            // Log error (using System.err for simplicity)
            System.err.println("Failed to process MQTT telemetry: " + e.getMessage());
        }
    }
}
