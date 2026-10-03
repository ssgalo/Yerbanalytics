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

/** Contrato MQTT del riel (design §1.1): el publicador arma el JSON exacto y no traga la falla. */
@DisplayName("ComandoRielPublisher")
class ComandoRielPublisherTest {

    private final MqttCommandGateway gateway = mock(MqttCommandGateway.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComandoRielPublisher publisher = new ComandoRielPublisher(gateway, mapper);

    @Test
    void irAPublicaEnElTopicoDelRielConLaPosicionLogica() throws Exception {
        ComandoRielPublisher.Resultado r = publisher.irA(2);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(topic.capture(), json.capture());
        assertThat(topic.getValue()).isEqualTo("nursery/rail/command");
        JsonNode n = mapper.readTree(json.getValue());
        assertThat(n.get("actuador").asText()).isEqualTo("rail");
        assertThat(n.get("accion").asText()).isEqualTo("IR_A");
        assertThat(n.get("parametros").get("posicion").asInt()).isEqualTo(2);
        assertThat(n.get("commandId").asText()).isEqualTo(r.commandId());
        assertThat(r.publicado()).isTrue();
        assertThat(r.error()).isNull();
    }

    @Test
    void homePublicaParametrosVacios() throws Exception {
        publisher.home();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(anyString(), json.capture());
        JsonNode n = mapper.readTree(json.getValue());
        assertThat(n.get("accion").asText()).isEqualTo("HOME");
        assertThat(n.get("parametros").isObject()).isTrue();
        assertThat(n.get("parametros").size()).isZero();
    }

    @Test
    void elCommandIdEsUnUuidV4YCambiaEnCadaComando() {
        String a = publisher.irA(1).commandId();
        String b = publisher.irA(1).commandId();

        assertThat(a).hasSize(36).isNotEqualTo(b);
        assertThat(UUID.fromString(a).version()).isEqualTo(4);
    }

    @Test
    void republicarUsaElMismoCommandId() throws Exception {
        publisher.irA(1, "5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(anyString(), json.capture());
        assertThat(mapper.readTree(json.getValue()).get("commandId").asText())
                .isEqualTo("5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10");
    }

    @Test
    void laFallaDelGatewayEsUnResultadoNoUnaExcepcion() {
        doThrow(new IllegalStateException("broker caído")).when(gateway).send(anyString(), anyString());

        ComandoRielPublisher.Resultado r = publisher.home();

        assertThat(r.publicado()).isFalse();
        assertThat(r.error()).contains("broker caído");
    }
}
