package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Contrato MQTT del comando de zona "leer ahora" (design §1.3): JSON exacto y la falla no se traga. */
@DisplayName("ComandoZonaPublisher")
class ComandoZonaPublisherTest {

    private final MqttCommandGateway gateway = mock(MqttCommandGateway.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComandoZonaPublisher publisher = new ComandoZonaPublisher(gateway, mapper);

    @Test
    void leerAhoraPublicaEnElTopicoDeLaZonaConElJsonDelContrato() throws Exception {
        ComandoZonaPublisher.Resultado r = publisher.leerAhora("MZ-1");

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(topic.capture(), json.capture());
        assertThat(topic.getValue()).isEqualTo("nursery/zone/MZ-1/command");
        JsonNode n = mapper.readTree(json.getValue());
        assertThat(n.size()).as("sin 'actuador': no es un actuador").isEqualTo(3);
        assertThat(n.get("commandId").asText()).isEqualTo(r.commandId());
        assertThat(n.get("accion").asText()).isEqualTo("LEER_AHORA");
        assertThat(n.get("parametros").isObject()).isTrue();
        assertThat(n.get("parametros").size()).isZero();
        assertThat(r.publicado()).isTrue();
        assertThat(r.error()).isNull();
    }

    @Test
    void elCommandIdEsUnUuidV4YCambiaEnCadaPedido() {
        String a = publisher.leerAhora("MZ-1").commandId();
        String b = publisher.leerAhora("MZ-1").commandId();

        assertThat(a).hasSize(36).isNotEqualTo(b);
        assertThat(UUID.fromString(a).version()).isEqualTo(4);
    }

    @Test
    void laFallaDelGatewayEsUnResultadoNoUnaExcepcion() {
        doThrow(new IllegalStateException("broker caído")).when(gateway).send(anyString(), anyString());

        ComandoZonaPublisher.Resultado r = publisher.leerAhora("MZ-1");

        assertThat(r.publicado()).isFalse();
        assertThat(r.error()).contains("broker caído");
    }

    @Test
    void lasConstantesEspejanElContrato() {
        assertThat(ContratoNodo.TOPIC_COMANDO_ZONA).isEqualTo("nursery/zone/%s/command");
        assertThat(ContratoNodo.ACCION_LEER_AHORA).isEqualTo("LEER_AHORA");
        assertThat(ContratoNodo.TOPIC_ACK).isEqualTo("nursery/zone/+/sector/+/ack");
        assertThat(ContratoNodo.STATUS_SUCCESS).isEqualTo("SUCCESS");
        assertThat(ContratoNodo.STATUS_ERROR).isEqualTo("ERROR");
    }
}
