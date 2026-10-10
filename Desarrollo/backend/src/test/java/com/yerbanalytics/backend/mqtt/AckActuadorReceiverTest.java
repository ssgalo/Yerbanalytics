package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.SeguimientoComandosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El receptor saca zona y sector del tópico, deserializa el ack del contrato y descarta lo ilegible. */
@DisplayName("AckActuadorReceiver")
class AckActuadorReceiverTest {

    private final SeguimientoComandosService seguimiento = mock(SeguimientoComandosService.class);
    private final AckActuadorReceiver receiver = new AckActuadorReceiver(seguimiento);

    private static Message<String> mensaje(String topic, String payload) {
        return MessageBuilder.withPayload(payload).setHeader("mqtt_receivedTopic", topic).build();
    }

    @Test
    void entregaElAckConLaZonaYElSectorDelTopico() {
        receiver.processMessage(mensaje("nursery/zone/MZ-1/sector/MZ-1-002/ack",
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\",\"detalle\":{\"tipo\":\"ok\",\"durationSec\":600,\"extra\":1}}"));

        ArgumentCaptor<AckActuador> ack = ArgumentCaptor.forClass(AckActuador.class);
        verify(seguimiento).registrarAck(eq("MZ-1"), eq("MZ-1-002"), ack.capture());
        assertThat(ack.getValue().commandId()).isEqualTo("abc");
        assertThat(ack.getValue().status()).isEqualTo("SUCCESS");
        assertThat(ack.getValue().tipoDetalle()).isEqualTo("ok");
        assertThat(ack.getValue().detalle().durationSec()).isEqualTo(600L);
    }

    @Test
    void unAckSinDetalleTambienSeEntrega() {
        receiver.processMessage(mensaje("nursery/zone/MZ-1/sector/MZ-1-001/ack",
                "{\"commandId\":\"abc\",\"status\":\"ERROR\"}"));

        ArgumentCaptor<AckActuador> ack = ArgumentCaptor.forClass(AckActuador.class);
        verify(seguimiento).registrarAck(eq("MZ-1"), eq("MZ-1-001"), ack.capture());
        assertThat(ack.getValue().tipoDetalle()).isNull();
    }

    @Test
    void unJsonIlegibleSeDescartaSinRomper() {
        receiver.processMessage(mensaje("nursery/zone/MZ-1/sector/MZ-1-001/ack", "{no es json"));

        verify(seguimiento, never()).registrarAck(anyString(), anyString(), any());
    }

    @Test
    void unTopicoQueNoEsDeAckSeDescarta() {
        receiver.processMessage(mensaje("nursery/zone/MZ-1/telemetry", "{\"commandId\":\"abc\",\"status\":\"SUCCESS\"}"));

        verify(seguimiento, never()).registrarAck(anyString(), anyString(), any());
    }
}
