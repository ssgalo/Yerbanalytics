package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.SecuenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Entrada del ACK de actuadores (design add-secuencias-demo-expo §1.2): sólo deserializa y delega. */
@DisplayName("AckActuadorReceiver")
class AckActuadorReceiverTest {

    private static final String TOPICO = "nursery/zone/MZ-1/sector/MZ-1-001/ack";

    private final SecuenciaService service = mock(SecuenciaService.class);
    private final AckActuadorReceiver receiver = new AckActuadorReceiver(service);

    private static Message<String> mensaje(String json, String topico) {
        return MessageBuilder.withPayload(json).setHeader("mqtt_receivedTopic", topico).build();
    }

    @Test
    void unAckValidoLlegaAlServicioConZonaYSectorDelTopico() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\",\"detalle\":{\"tipo\":\"ok\",\"durationSec\":20}}",
                TOPICO));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(service).registrarAck(c.capture());
        AckActuador ack = c.getValue();
        assertThat(ack.commandId()).isEqualTo("abc");
        assertThat(ack.status()).isEqualTo("SUCCESS");
        assertThat(ack.zonaId()).isEqualTo("MZ-1");
        assertThat(ack.sectorId()).isEqualTo("MZ-1-001");
    }

    @Test
    void elDetalleLlegaComoArbolJson() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"ERROR\",\"detalle\":{\"tipo\":\"falla_mecanica\"}}", TOPICO));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(service).registrarAck(c.capture());
        assertThat(c.getValue().detalle().isObject()).isTrue();
        assertThat(c.getValue().detalle().get("tipo").asText()).isEqualTo("falla_mecanica");
    }

    @Test
    void toleraClavesDesconocidasYDetalleAusente() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\",\"rssi\":-60,\"algoNuevo\":{\"x\":1}}", TOPICO));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(service).registrarAck(c.capture());
        assertThat(c.getValue().commandId()).isEqualTo("abc");
        assertThat(c.getValue().detalle()).isNull();
    }

    @Test
    void unJsonRotoNoLanzaNiLlamaAlServicio() {
        assertThatCode(() -> receiver.processMessage(mensaje("{no es json", TOPICO)))
                .doesNotThrowAnyException();

        verify(service, never()).registrarAck(any());
    }

    @Test
    void unPayloadQueNoEsUnObjetoSeDescarta() {
        assertThatCode(() -> receiver.processMessage(mensaje("[1,2,3]", TOPICO))).doesNotThrowAnyException();

        verify(service, never()).registrarAck(any());
    }
}
