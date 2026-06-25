package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.NurseryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttTelemetryReceiver {

    private final NurseryService nurseryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
