package com.yerbanalytics.backend.mqtt;

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@IntegrationComponentScan
public class MqttConfig {

    @Value("${yerbanalytics.mqtt.host}")
    private String brokerUrl;

    @Value("${yerbanalytics.mqtt.client-id}")
    private String clientId;

    @Value("${yerbanalytics.mqtt.telemetry-topic}")
    private String telemetryTopic;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttConnectOptions options) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        factory.setPersistence(new MemoryPersistence());
        return factory;
    }

    @Bean(name = "mqttInputChannel")
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter inboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-inbound", factory, telemetryTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttMessageFlow() {
        return IntegrationFlow.from(mqttInputChannel())
                .handle("mqttTelemetryReceiver", "processMessage")
                .get();
    }

    // -------------------------------------------------------------------------
    // Outbound: publicación de comandos hacia los nodos actuadores (Downlink)
    // -------------------------------------------------------------------------

    /**
     * Canal de salida al que el {@link MqttCommandGateway} entrega los mensajes.
     * El {@link MqttPahoMessageHandler} los consume y los publica al broker.
     */
    @Bean(name = "mqttOutboundChannel")
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * Adapter de salida hacia el broker MQTT.
     *
     * <p>Usa el mismo {@link MqttPahoClientFactory} que el inbound, pero con un
     * {@code clientId} distinto para evitar colisión de sesiones en el broker.
     *
     * <p>El tópico se sobrescribe en cada mensaje vía {@code MqttHeaders.TOPIC},
     * permitiendo apuntar al sector correcto sin necesitar múltiples beans.
     *
     * <p>QoS 1 (al menos una vez) es el máximo soportado de forma fiable por
     * la biblioteca {@code org.eclipse.paho.client.mqttv3} con retención en memoria.
     * El nodo actuador ESP32 ya es idempotente por diseño (ignora el segundo comando
     * si el actuador ya está en el estado solicitado).
     */
    @Bean
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId + "-outbound", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        return handler;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow(MessageHandler mqttOutboundHandler) {
        return IntegrationFlow.from(mqttOutboundChannel())
                .handle(mqttOutboundHandler)
                .get();
    }
}
