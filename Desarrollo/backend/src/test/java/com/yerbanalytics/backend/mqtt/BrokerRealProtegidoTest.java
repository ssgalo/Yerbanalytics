package com.yerbanalytics.backend.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

/**
 * Guarda de seguridad: los tests nunca deben hablar con el broker MQTT real (el del ESP32 físico).
 * Un contexto de test que apunte a localhost:1883 movió actuadores reales (2026-10-23).
 */
@SpringBootTest
class BrokerRealProtegidoTest {

    @Value("${yerbanalytics.mqtt.host}")
    private String host;

    @Value("${yerbanalytics.mqtt.enabled}")
    private boolean enabled;

    @Autowired
    private MqttConnectOptions options;

    @Autowired
    private MqttPahoMessageDrivenChannelAdapter inboundAdapter;

    @Test
    void elContextoDeTestNoApuntaAlBrokerReal() {
        assertThat(host).doesNotContain("localhost").doesNotContain("127.0.0.1").doesNotContain(":1883");
        assertThat(options.getServerURIs()).doesNotContain("tcp://localhost:1883");
    }

    @Test
    void elMqttEstaDesactivadoEnTests() {
        assertThat(enabled).isFalse();
        assertThat(inboundAdapter.isAutoStartup()).isFalse();
        assertThat(inboundAdapter.isRunning()).isFalse();
    }
}
