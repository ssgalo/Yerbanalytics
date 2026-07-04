package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica telemetría en el broker MQTT real, en el topic de una macro-zona
 * ({@code nursery/zone/{zonaId}/telemetry}). Centraliza la mecánica de
 * conectar/publicar/desconectar que comparten el simulador automático
 * ({@link MqttTelemetrySimulator}) y el envío manual del dashboard de simulación.
 *
 * <p>El mensaje atraviesa el mismo pipeline de ingesta que el hardware físico
 * (adapter inbound → {@link MqttTelemetryReceiver} → NurseryService), de modo que
 * un envío manual es indistinguible de un ESP32 real.
 */
@Component
public class MqttTelemetryPublisher {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${yerbanalytics.mqtt.host}")
    private String brokerUrl;

    @Value("${yerbanalytics.mqtt.telemetry-topic}")
    private String telemetryTopicTemplate;

    @Value("${yerbanalytics.mqtt.simulator.client-id}")
    private String clientIdBase;

    /**
     * Publica un payload en el topic de la macro-zona indicada. Abre una conexión efímera
     * (QoS 1) y la cierra al terminar. Propaga la excepción para que el llamador la maneje.
     */
    public void publish(String zonaId, MqttTelemetryPayload payload) throws Exception {
        // clientId único por publicación: evita colisiones con el inbound adapter y con envíos concurrentes.
        String clientId = clientIdBase + "-pub-" + System.nanoTime();
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.connect(options);

            String json = mapper.writeValueAsString(payload);
            String topic = telemetryTopicTemplate.replace("+", zonaId);
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            client.publish(topic, message);
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
