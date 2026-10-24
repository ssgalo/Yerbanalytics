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

    /**
     * Interruptor de toda la conexión al broker. Por defecto (producción) está encendido; los tests lo
     * apagan para no conectarse jamás al broker real, que es el del ESP32 físico: ni suscripciones ni
     * publicaciones.
     */
    @Value("${yerbanalytics.mqtt.enabled:true}")
    private boolean enabled;

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
        adapter.setAutoStartup(enabled);
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
    // Inbound del riel: eventos del ESP32 que mueve la cámara (nursery/rail/event)
    // -------------------------------------------------------------------------

    /**
     * Adaptador <strong>propio</strong> (y no un {@code addTopic} al de telemetría) para que el
     * camino de la telemetría, el que alimenta al motor de reglas, quede idéntico. Su clientId lleva
     * sufijo {@code -riel} para no colisionar sesiones en el broker.
     */
    @Bean(name = "mqttRielChannel")
    public MessageChannel mqttRielChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter rielInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-riel", factory, ContratoRiel.TOPIC_EVENTO);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setAutoStartup(enabled);
        adapter.setOutputChannel(mqttRielChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttRielFlow() {
        return IntegrationFlow.from(mqttRielChannel())
                .handle("rielEventoReceiver", "processMessage")
                .get();
    }

    // -------------------------------------------------------------------------
    // Inbound de los acks: resultado de cada comando de riego, insumo o mediasombra
    // (nursery/zone/{zona}/sector/{sector}/ack; la mediasombra, de la zona: nursery/zone/{zona}/ack)
    // -------------------------------------------------------------------------

    /**
     * Adaptador propio, como el del riel: el camino de la telemetría queda idéntico. Su clientId
     * lleva sufijo {@code -ack} para no colisionar sesiones en el broker.
     */
    @Bean(name = "mqttAckChannel")
    public MessageChannel mqttAckChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter ackInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-ack", factory,
                        ContratoNodo.TOPIC_ACK_SUSCRIPCION, ContratoNodo.TOPIC_ACK_ZONA_SUSCRIPCION);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setAutoStartup(enabled);
        adapter.setOutputChannel(mqttAckChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttAckFlow() {
        return IntegrationFlow.from(mqttAckChannel())
                .handle("ackActuadorReceiver", "processMessage")
                .get();
    }

    // -------------------------------------------------------------------------
    // Inbound de los errores del nodo: fallas que el ESP32 detecta y publica una vez por evento
    // (nursery/zone/{zona}/errores, S-05)
    // -------------------------------------------------------------------------

    /**
     * Adaptador propio, como el del riel y el de los acks: el camino de la telemetría queda idéntico. Su clientId
     * lleva sufijo {@code -errores} para no colisionar sesiones en el broker.
     */
    @Bean(name = "mqttErrorChannel")
    public MessageChannel mqttErrorChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter errorInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId + "-errores", factory, ContratoNodo.TOPIC_ERRORS_SUBSCRIPTION);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setAutoStartup(enabled);
        adapter.setOutputChannel(mqttErrorChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttErrorFlow() {
        return IntegrationFlow.from(mqttErrorChannel())
                .handle("nodeErrorReceiver", "processMessage")
                .get();
    }

    /**
     * Segunda suscripción al <strong>mismo</strong> tópico de telemetría que la ingesta, con su propio
     * cliente ({@code -lectura}): así la telemetría que alimenta al motor queda byte a byte igual y la
     * secuencia de lectura se entera de la respuesta sin acoplarse a {@code NurseryService}. Costo: el
     * broker entrega cada lectura dos veces al backend.
     */
    @Bean(name = "mqttLecturaChannel")
    public MessageChannel mqttLecturaChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter lecturaInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-lectura", factory, telemetryTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setAutoStartup(enabled);
        adapter.setOutputChannel(mqttLecturaChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttLecturaFlow() {
        return IntegrationFlow.from(mqttLecturaChannel())
                .handle("lecturaZonaReceiver", "processMessage")
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
        if (!enabled) {
            // Sin broker: el comando se descarta en lugar de salir a la red.
            return message -> { };
        }
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
