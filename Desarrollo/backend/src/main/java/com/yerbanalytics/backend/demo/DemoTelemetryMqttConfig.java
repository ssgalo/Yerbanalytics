package com.yerbanalytics.backend.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

/**
 * Suscripciones al tópico de demo ({@link DemoTelemetryTopics#TELEMETRY_SUBSCRIPTION}). Sólo con
 * {@code yerbanalytics.demo.enabled=true}. Espeja los dos adaptadores de la telemetría real (ingesta y lectura de las
 * secuencias) y vuelca en los <strong>mismos</strong> canales, así el procesamiento es idéntico y la compuerta
 * ({@link DemoTelemetrySourceStore}) decide qué pasa.
 */
@Configuration
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoTelemetryMqttConfig {

    @Value("${yerbanalytics.mqtt.client-id}")
    private String clientId;

    /** Mismo interruptor que {@code MqttConfig}: apagado, ningún adaptador arranca (tests). */
    @Value("${yerbanalytics.mqtt.enabled:true}")
    private boolean enabled;

    @Bean
    public MqttPahoMessageDrivenChannelAdapter demoInboundAdapter(
            MqttPahoClientFactory factory, @Qualifier("mqttInputChannel") MessageChannel mqttInputChannel) {
        return adapter("-demo-inbound", factory, mqttInputChannel);
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter demoLecturaInboundAdapter(
            MqttPahoClientFactory factory, @Qualifier("mqttLecturaChannel") MessageChannel mqttLecturaChannel) {
        return adapter("-demo-lectura", factory, mqttLecturaChannel);
    }

    private MqttPahoMessageDrivenChannelAdapter adapter(String suffix, MqttPahoClientFactory factory,
                                                        MessageChannel output) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId + suffix, factory, DemoTelemetryTopics.TELEMETRY_SUBSCRIPTION);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setAutoStartup(enabled);
        adapter.setOutputChannel(output);
        return adapter;
    }
}
