package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.SecuenciaService;
import com.yerbanalytics.backend.service.SeguimientoComandosService;
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

/**
 * El receptor saca zona y sector del tópico, deserializa el ack del contrato una sola vez, lo entrega a
 * los DOS consumidores (seguimiento de comandos y secuencias) y descarta lo ilegible.
 */
@DisplayName("AckActuadorReceiver")
class AckActuadorReceiverTest {

    private static final String TOPICO = "nursery/zone/MZ-1/sector/MZ-1-001/ack";

    private final SecuenciaService secuencias = mock(SecuenciaService.class);
    private final SeguimientoComandosService seguimiento = mock(SeguimientoComandosService.class);
    private final AckActuadorReceiver receiver = new AckActuadorReceiver(secuencias, seguimiento);

    private static Message<String> mensaje(String json, String topico) {
        return MessageBuilder.withPayload(json).setHeader("mqtt_receivedTopic", topico).build();
    }

    @Test
    void unAckValidoLlegaAAmbosConsumidoresConZonaYSectorDelTopico() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\",\"detalle\":{\"tipo\":\"ok\",\"durationSec\":20}}",
                "nursery/zone/MZ-1/sector/MZ-1-002/ack"));

        ArgumentCaptor<AckActuador> deSecuencias = ArgumentCaptor.forClass(AckActuador.class);
        ArgumentCaptor<AckActuador> deSeguimiento = ArgumentCaptor.forClass(AckActuador.class);
        verify(secuencias).registrarAck(deSecuencias.capture());
        verify(seguimiento).registrarAck(deSeguimiento.capture());
        assertThat(deSeguimiento.getValue()).isEqualTo(deSecuencias.getValue());
        AckActuador ack = deSecuencias.getValue();
        assertThat(ack.commandId()).isEqualTo("abc");
        assertThat(ack.status()).isEqualTo("SUCCESS");
        assertThat(ack.zonaId()).isEqualTo("MZ-1");
        assertThat(ack.sectorId()).isEqualTo("MZ-1-002");
        assertThat(ack.tipoDetalle()).isEqualTo("ok");
        assertThat(ack.detalle().get("durationSec").asLong()).isEqualTo(20L);
    }

    @Test
    void elDetalleLlegaComoArbolJson() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"ERROR\",\"detalle\":{\"tipo\":\"falla_mecanica\"}}", TOPICO));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(secuencias).registrarAck(c.capture());
        assertThat(c.getValue().detalle().isObject()).isTrue();
        assertThat(c.getValue().detalle().get("tipo").asText()).isEqualTo("falla_mecanica");
        assertThat(c.getValue().tipoDetalle()).isEqualTo("falla_mecanica");
    }

    @Test
    void toleraClavesDesconocidasYDetalleAusente() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\",\"rssi\":-60,\"algoNuevo\":{\"x\":1}}", TOPICO));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(seguimiento).registrarAck(c.capture());
        verify(secuencias).registrarAck(any());
        assertThat(c.getValue().commandId()).isEqualTo("abc");
        assertThat(c.getValue().detalle()).isNull();
        assertThat(c.getValue().tipoDetalle()).isNull();
    }

    @Test
    void unJsonRotoNoLanzaNiLlamaANadie() {
        assertThatCode(() -> receiver.processMessage(mensaje("{no es json", TOPICO)))
                .doesNotThrowAnyException();

        verify(secuencias, never()).registrarAck(any());
        verify(seguimiento, never()).registrarAck(any());
    }

    @Test
    void unPayloadQueNoEsUnObjetoSeDescarta() {
        assertThatCode(() -> receiver.processMessage(mensaje("[1,2,3]", TOPICO))).doesNotThrowAnyException();

        verify(secuencias, never()).registrarAck(any());
        verify(seguimiento, never()).registrarAck(any());
    }

    @Test
    void unTopicoQueNoEsDeAckSeDescarta() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\"}", "nursery/zone/MZ-1/telemetry"));

        verify(secuencias, never()).registrarAck(any());
        verify(seguimiento, never()).registrarAck(any());
    }

    @Test
    void siUnConsumidorFallaElOtroIgualRecibeElAck() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(seguimiento).registrarAck(any());

        assertThatCode(() -> receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"SUCCESS\"}", TOPICO))).doesNotThrowAnyException();

        verify(secuencias).registrarAck(any());
    }

    @Test
    void unEnCursoLlegaAAmbosConsumidoresConSuDetalle() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"abc\",\"status\":\"EN_CURSO\",\"detalle\":{\"tipo\":\"en_cola\",\"posicion\":2,\"esperaMaxSec\":45}}",
                TOPICO));

        ArgumentCaptor<AckActuador> deSecuencias = ArgumentCaptor.forClass(AckActuador.class);
        ArgumentCaptor<AckActuador> deSeguimiento = ArgumentCaptor.forClass(AckActuador.class);
        verify(secuencias).registrarAck(deSecuencias.capture());
        verify(seguimiento).registrarAck(deSeguimiento.capture());
        assertThat(deSeguimiento.getValue()).isEqualTo(deSecuencias.getValue());
        AckActuador ack = deSeguimiento.getValue();
        assertThat(ack.esIntermedio()).isTrue();
        assertThat(ack.tipoDetalle()).isEqualTo("en_cola");
        assertThat(ack.esperaMaxSec()).isEqualTo(45L);
    }
    @Test
    void elAckDeLaMallaLlegaPorElTopicoDeZonaYSinSector() {
        receiver.processMessage(mensaje(
                "{\"commandId\":\"m-1\",\"status\":\"SUCCESS\",\"detalle\":{\"tipo\":\"ok\",\"targetPct\":100}}",
                "nursery/zone/MZ-3/ack"));

        ArgumentCaptor<AckActuador> c = ArgumentCaptor.forClass(AckActuador.class);
        verify(seguimiento).registrarAck(c.capture());
        verify(secuencias).registrarAck(any());
        assertThat(c.getValue().zonaId()).isEqualTo("MZ-3");
        assertThat(c.getValue().sectorId()).isNull();
        assertThat(c.getValue().commandId()).isEqualTo("m-1");
    }
}
