package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MqttTelemetrySimulator {

    private final Random random = new Random();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${yerbanalytics.mqtt.host}")
    private String brokerUrl;

    @Value("${yerbanalytics.mqtt.simulator.client-id}")
    private String clientId;

    @Value("${yerbanalytics.mqtt.telemetry-topic}")
    private String telemetryTopicTemplate;

    @Value("${yerbanalytics.mqtt.simulator.enabled}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${yerbanalytics.mqtt.simulator.interval-ms}")
    public void publishTelemetry() {
        if (!enabled) {
            return;
        }
        try {
            MqttClient client = new MqttClient(brokerUrl, clientId + "-simulator", new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.connect(options);

            // Simulate zones MZ-1 to MZ-6
            for (int i = 1; i <= 6; i++) {
                String zoneId = "MZ-" + i;
                MqttTelemetryPayload payload = new MqttTelemetryPayload(
                        "AA:BB:CC:DD:EE:FF",
                        80 + random.nextInt(20), // battery 80-99
                        System.currentTimeMillis(),
                        new MqttTelemetryPayload.MetricsPayload(
                                38.0 + random.nextDouble() * 25, // humSus (ideal is 42-68)
                                58.0 + random.nextDouble() * 22, // humAmb (ideal is 62-84)
                                17.0 + random.nextDouble() * 11, // temp (ideal is 18-27)
                                0.9 + random.nextDouble() * 0.9,  // ce (ideal is 1.0-1.9)
                                0.5 + random.nextDouble() * 6.5  // uv (ideal is 1.0-6.0)
                        )
                );
                String json = mapper.writeValueAsString(payload);
                String topic = telemetryTopicTemplate.replace("+", zoneId);
                MqttMessage message = new MqttMessage(json.getBytes());
                message.setQos(1);
                client.publish(topic, message);
            }
            client.disconnect();
        } catch (Exception e) {
            System.err.println("[MqttTelemetrySimulator] Error publishing telemetry: " + e.getMessage());
        }
    }
}
