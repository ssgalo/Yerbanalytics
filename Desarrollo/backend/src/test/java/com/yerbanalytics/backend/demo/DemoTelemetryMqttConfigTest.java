package com.yerbanalytics.backend.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.yerbanalytics.backend.mqtt.TelemetryAdmission;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("DemoTelemetryMqttConfig")
class DemoTelemetryMqttConfigTest {

    @Configuration
    static class Base {
        @Bean
        MqttPahoClientFactory mqttClientFactory() {
            return mock(MqttPahoClientFactory.class);
        }

        /** No se conecta a ningún broker: sólo se verifica cómo quedan armados. */
        @Bean
        static org.springframework.beans.factory.config.BeanPostProcessor sinArranque() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof MqttPahoMessageDrivenChannelAdapter a) {
                        a.setAutoStartup(false);
                    }
                    return bean;
                }
            };
        }

        @Bean(name = "mqttInputChannel")
        MessageChannel mqttInputChannel() {
            return new DirectChannel();
        }

        @Bean(name = "mqttLecturaChannel")
        MessageChannel mqttLecturaChannel() {
            return new DirectChannel();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Base.class, DemoTelemetryMqttConfig.class, DemoTelemetrySourceStore.class)
            .withPropertyValues("yerbanalytics.mqtt.client-id=yerba");

    @Test
    void conDemoApagadoNoExisteNingunaPiezaDeLaFuenteDeTelemetria() {
        runner.withPropertyValues("yerbanalytics.demo.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(MqttPahoMessageDrivenChannelAdapter.class);
            assertThat(ctx).doesNotHaveBean(DemoTelemetrySourceStore.class);
            assertThat(ctx).doesNotHaveBean(TelemetryAdmission.class);
        });
    }

    @Test
    void sinLaPropiedadTampocoExiste() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MqttPahoMessageDrivenChannelAdapter.class));
    }

    @Test
    void conDemoEncendidoHayDosAdaptadoresSobreElTopicoDeDemoConSuPropioCliente() {
        runner.withPropertyValues("yerbanalytics.demo.enabled=true").run(ctx -> {
            assertThat(ctx.getBeansOfType(MqttPahoMessageDrivenChannelAdapter.class)).hasSize(2);
            MqttPahoMessageDrivenChannelAdapter ingesta = ctx.getBean("demoInboundAdapter",
                    MqttPahoMessageDrivenChannelAdapter.class);
            MqttPahoMessageDrivenChannelAdapter lectura = ctx.getBean("demoLecturaInboundAdapter",
                    MqttPahoMessageDrivenChannelAdapter.class);

            assertThat((Object) ReflectionTestUtils.invokeMethod(ingesta, "getClientId")).isEqualTo("yerba-demo-inbound");
            assertThat((Object) ReflectionTestUtils.invokeMethod(lectura, "getClientId")).isEqualTo("yerba-demo-lectura");
            assertThat(ingesta.getTopic()).containsExactly("nursery/demo/zone/+/telemetry");
            assertThat(lectura.getTopic()).containsExactly("nursery/demo/zone/+/telemetry");
            assertThat(ingesta.getOutputChannel()).isSameAs(ctx.getBean("mqttInputChannel"));
            assertThat(lectura.getOutputChannel()).isSameAs(ctx.getBean("mqttLecturaChannel"));
            assertThat(ctx).hasSingleBean(TelemetryAdmission.class);
        });
    }
}
