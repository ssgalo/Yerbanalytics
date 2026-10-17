package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.HardwareFaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El receptor saca la zona del tópico, valida el payload contra el contrato (S-05) y entrega el error al servicio de
 * fallas. Un tipo que el contrato no define, o un mensaje ilegible, se descarta con un log y no cambia ningún estado.
 */
@DisplayName("NodeErrorReceiver")
class NodeErrorReceiverTest {

    private static final String TOPICO = "nursery/zone/MZ-1/errores";

    private final HardwareFaultService faults = mock(HardwareFaultService.class);
    private final NodeErrorReceiver receiver = new NodeErrorReceiver(faults);

    private static Message<String> mensaje(String json, String topico) {
        return MessageBuilder.withPayload(json).setHeader("mqtt_receivedTopic", topico).build();
    }

    private NodeError entregado() {
        ArgumentCaptor<NodeError> captor = ArgumentCaptor.forClass(NodeError.class);
        verify(faults).report(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("un error de caudal llega al servicio con la zona del tópico y el sector del payload")
    void errorDeCaudal() {
        receiver.processMessage(mensaje(
                "{\"nodo\":\"A4:CF\",\"sector\":\"MZ-1-012\",\"tipo\":\"SIN_CAUDAL\",\"valor\":0,\"timestamp\":1760000000}", TOPICO));

        NodeError error = entregado();
        assertThat(error.zoneId()).isEqualTo("MZ-1");
        assertThat(error.node()).isEqualTo("A4:CF");
        assertThat(error.sectorId()).isEqualTo("MZ-1-012");
        assertThat(error.type()).isEqualTo(NodeErrorType.NO_FLOW);
        assertThat(error.value()).isEqualTo("0");
    }

    @Test
    @DisplayName("la zona sale del tópico aunque el payload diga otra cosa")
    void laZonaEsLaDelTopico() {
        receiver.processMessage(mensaje(
                "{\"zona\":\"MZ-9\",\"sector\":\"MZ-1-012\",\"tipo\":\"CAUDAL_EXCESIVO\",\"valor\":182.5}", "nursery/zone/MZ-3/errores"));

        NodeError error = entregado();
        assertThat(error.zoneId()).isEqualTo("MZ-3");
        assertThat(error.type()).isEqualTo(NodeErrorType.EXCESS_FLOW);
        assertThat(error.value()).isEqualTo("182.5");
    }

    @Test
    @DisplayName("el error de la malla trae el final de carrera como texto y el sector vacío")
    void errorDeLaMalla() {
        receiver.processMessage(mensaje(
                "{\"nodo\":\"A4:CF\",\"sector\":\"\",\"tipo\":\"MEDIASOMBRA_FIN_DE_CARRERA\",\"valor\":\"CERRADA\"}", TOPICO));

        NodeError error = entregado();
        assertThat(error.type()).isEqualTo(NodeErrorType.SHADE_LIMIT_SWITCH);
        assertThat(error.sectorId()).isEmpty();
        assertThat(error.value()).isEqualTo("CERRADA");
    }

    @Test
    @DisplayName("tolera claves desconocidas y campos ausentes")
    void toleranteConLoOpcional() {
        receiver.processMessage(mensaje("{\"tipo\":\"SIN_CAUDAL\",\"sector\":\"MZ-1-001\",\"firmware\":\"2.1\"}", TOPICO));

        NodeError error = entregado();
        assertThat(error.node()).isNull();
        assertThat(error.value()).isNull();
    }

    @Test
    @DisplayName("un tipo que el contrato no define se descarta sin llegar al servicio")
    void tipoDesconocido() {
        receiver.processMessage(mensaje("{\"tipo\":\"EXPLOSION\",\"sector\":\"MZ-1-012\"}", TOPICO));
        receiver.processMessage(mensaje("{\"sector\":\"MZ-1-012\"}", TOPICO));
        receiver.processMessage(mensaje("{\"tipo\":\"sin_caudal\",\"sector\":\"MZ-1-012\"}", TOPICO));

        verify(faults, never()).report(any());
    }

    @Test
    @DisplayName("lo ilegible o en un tópico inesperado se descarta y nunca tira abajo el adaptador")
    void ilegible() {
        assertThatCode(() -> {
            receiver.processMessage(mensaje("esto no es json", TOPICO));
            receiver.processMessage(mensaje("[1,2,3]", TOPICO));
            receiver.processMessage(mensaje("{\"tipo\":\"SIN_CAUDAL\"}", "nursery/zone/MZ-1/sector/MZ-1-001/errores"));
            receiver.processMessage(mensaje("{\"tipo\":\"SIN_CAUDAL\"}", "nursery/zone/MZ-1/telemetry"));
            receiver.processMessage(MessageBuilder.withPayload("{\"tipo\":\"SIN_CAUDAL\"}").build());
        }).doesNotThrowAnyException();

        verify(faults, never()).report(any());
    }

    @Test
    @DisplayName("si el servicio falla, el mensaje se pierde con un log pero el adaptador sigue")
    void falloDelServicio() {
        doThrow(new IllegalStateException("base caída")).when(faults).report(any());

        assertThatCode(() -> receiver.processMessage(
                mensaje("{\"tipo\":\"SIN_CAUDAL\",\"sector\":\"MZ-1-012\"}", TOPICO))).doesNotThrowAnyException();
    }
}
