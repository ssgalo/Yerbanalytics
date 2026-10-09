package com.yerbanalytics.backend.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Los dos adaptadores nuevos (design add-secuencias-demo-expo §2.2): cada uno con su clientId, para no
 * pisar sesiones en el broker, y los dos de siempre (telemetría y riel) sin cambios. No se arranca
 * ninguno: sólo se verifica cómo quedan armados.
 */
@DisplayName("MqttConfig - adaptadores de las secuencias")
class MqttConfigSecuenciasTest {

    private final MqttPahoClientFactory factory = mock(MqttPahoClientFactory.class);
    private final MqttConfig config = new MqttConfig();

    MqttConfigSecuenciasTest() {
        ReflectionTestUtils.setField(config, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(config, "clientId", "yerba");
        ReflectionTestUtils.setField(config, "telemetryTopic", "nursery/zone/+/telemetry");
    }

    /** El clientId es protegido en el adaptador: se lee por reflexión. */
    private static Object clientIdDe(MqttPahoMessageDrivenChannelAdapter adapter) {
        return ReflectionTestUtils.invokeMethod(adapter, "getClientId");
    }

    @Test
    void elAdaptadorDeAcksEscuchaElTopicoDeAcksConSuPropioCliente() {
        MqttPahoMessageDrivenChannelAdapter a = config.ackInboundAdapter(factory);

        assertThat(clientIdDe(a)).isEqualTo("yerba-ack");
        assertThat(a.getTopic()).containsExactly("nursery/zone/+/sector/+/ack");
        assertThat(a.getQos()).containsExactly(1);
        assertThat(a.getOutputChannel()).isNotNull();
    }

    @Test
    void elAdaptadorDeLecturaEscuchaLaMismaTelemetriaQueLaIngestaConOtroCliente() {
        MqttPahoMessageDrivenChannelAdapter lectura = config.lecturaInboundAdapter(factory);
        MqttPahoMessageDrivenChannelAdapter ingesta = config.inboundAdapter(factory);

        assertThat(clientIdDe(lectura)).isEqualTo("yerba-lectura").isNotEqualTo(clientIdDe(ingesta));
        assertThat(lectura.getTopic()).containsExactly("nursery/zone/+/telemetry").isEqualTo(ingesta.getTopic());
        assertThat(lectura.getQos()).containsExactly(1);
        assertThat(lectura.getOutputChannel()).isNotNull();
    }

    @Test
    void losAdaptadoresDeTelemetriaYRielSiguenIgual() {
        MqttPahoMessageDrivenChannelAdapter ingesta = config.inboundAdapter(factory);
        MqttPahoMessageDrivenChannelAdapter riel = config.rielInboundAdapter(factory);

        assertThat(clientIdDe(ingesta)).isEqualTo("yerba-inbound");
        assertThat(ingesta.getTopic()).containsExactly("nursery/zone/+/telemetry");
        assertThat(clientIdDe(riel)).isEqualTo("yerba-riel");
        assertThat(riel.getTopic()).containsExactly("nursery/rail/event");
    }
}
