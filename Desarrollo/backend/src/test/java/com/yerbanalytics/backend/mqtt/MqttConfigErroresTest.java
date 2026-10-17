package com.yerbanalytics.backend.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El adaptador de los errores del nodo (S-05): propio y con su cliente, como los de ack y riel, para que el camino de la
 * telemetría que alimenta al motor quede idéntico. No se arranca: sólo se verifica cómo queda armado.
 */
@DisplayName("MqttConfig - adaptador de los errores del nodo")
class MqttConfigErroresTest {

    private final MqttPahoClientFactory factory = mock(MqttPahoClientFactory.class);
    private final MqttConfig config = new MqttConfig();

    MqttConfigErroresTest() {
        ReflectionTestUtils.setField(config, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(config, "clientId", "yerba");
        ReflectionTestUtils.setField(config, "telemetryTopic", "nursery/zone/+/telemetry");
    }

    @Test
    void escuchaElTopicoDeErroresConSuPropioClienteYQos1() {
        MqttPahoMessageDrivenChannelAdapter a = config.errorInboundAdapter(factory);

        assertThat((Object) ReflectionTestUtils.invokeMethod(a, "getClientId")).isEqualTo("yerba-errores");
        assertThat(a.getTopic()).containsExactly("nursery/zone/+/errores");
        assertThat(a.getQos()).containsExactly(1);
        assertThat(a.getOutputChannel()).isNotNull();
    }

    @Test
    void elClienteNoPisaALosDemasAdaptadores() {
        Object errores = ReflectionTestUtils.invokeMethod(config.errorInboundAdapter(factory), "getClientId");

        assertThat(errores)
                .isNotEqualTo(ReflectionTestUtils.invokeMethod(config.ackInboundAdapter(factory), "getClientId"))
                .isNotEqualTo(ReflectionTestUtils.invokeMethod(config.inboundAdapter(factory), "getClientId"))
                .isNotEqualTo(ReflectionTestUtils.invokeMethod(config.rielInboundAdapter(factory), "getClientId"));
    }
}
