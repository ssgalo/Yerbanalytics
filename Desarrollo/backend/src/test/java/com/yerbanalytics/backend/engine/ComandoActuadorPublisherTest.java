package com.yerbanalytics.backend.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.mqtt.MqttCommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Tarea 8.2: el publicador arma el payload del contrato y NO traga la falla del gateway. */
@DisplayName("ComandoActuadorPublisher")
class ComandoActuadorPublisherTest {

    private final MqttCommandGateway gateway = mock(MqttCommandGateway.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComandoActuadorPublisher publisher = new ComandoActuadorPublisher(gateway, mapper);

    @Test
    void armaElPayloadDelContratoYLoPublicaEnElTopicoDelSector() throws Exception {
        ComandoActuadorPublisher.Resultado r =
                publisher.publicar("MZ-2", "MZ-2-001", "valve", "ON", Map.of("durationSec", 504));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(topic.capture(), json.capture());
        assertThat(topic.getValue()).isEqualTo("nursery/zone/MZ-2/sector/MZ-2-001/command");
        JsonNode n = mapper.readTree(json.getValue());
        assertThat(n.get("actuador").asText()).isEqualTo("valve");
        assertThat(n.get("accion").asText()).isEqualTo("ON");
        assertThat(n.get("parametros").get("durationSec").asInt()).isEqualTo(504);
        assertThat(n.get("commandId").asText()).isNotBlank();
        assertThat(r.publicado()).isTrue();
        assertThat(r.commandId()).isEqualTo(n.get("commandId").asText());
        assertThat(r.error()).isNull();
    }

    @Test
    void cadaComandoLlevaUnCommandIdDistinto() {
        String a = publisher.publicar("MZ-1", "MZ-1-001", "pump", "ON", Map.of()).commandId();
        String b = publisher.publicar("MZ-1", "MZ-1-001", "pump", "ON", Map.of()).commandId();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void laFallaDelGatewayEsUnResultadoNoUnaExcepcionNiUnExitoFalso() {
        doThrow(new IllegalStateException("broker caído")).when(gateway).send(anyString(), anyString());

        ComandoActuadorPublisher.Resultado r = publisher.publicar("MZ-1", "MZ-1-001", "valve", "ON", Map.of("durationSec", 60));

        assertThat(r.publicado()).isFalse();
        assertThat(r.error()).contains("broker caído");
    }

    @Test
    void unPayloadQueNoSeSerializaEsUnaFalla() {
        Map<String, Object> noSerializable = Map.of("x", new Object() {
            @SuppressWarnings("unused")
            public Object getBoom() { throw new IllegalStateException("no se serializa"); }
        });

        ComandoActuadorPublisher.Resultado r = publisher.publicar("MZ-1", "MZ-1-001", "shade", "SET", noSerializable);

        assertThat(r.publicado()).isFalse();
        assertThat(r.error()).isNotBlank();
    }
}
