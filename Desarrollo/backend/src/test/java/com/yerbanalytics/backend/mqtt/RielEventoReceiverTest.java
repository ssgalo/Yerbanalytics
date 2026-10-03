package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.PasadaRielService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RielEventoReceiver")
class RielEventoReceiverTest {

    private final PasadaRielService service = mock(PasadaRielService.class);
    private final RielEventoReceiver receiver = new RielEventoReceiver(service);

    @Test
    void unEventoValidoLlegaAlServicioConTodosSusCampos() {
        receiver.processMessage(MessageBuilder.withPayload(
                "{\"commandId\":\"abc\",\"status\":\"ERROR\",\"posicion\":null,\"pasos\":17345,"
                        + "\"codigo\":\"FIN_DE_CARRERA\",\"detalle\":\"tocó el final\"}").build());

        ArgumentCaptor<EventoRiel> c = ArgumentCaptor.forClass(EventoRiel.class);
        verify(service).registrarEvento(c.capture());
        EventoRiel e = c.getValue();
        assertThat(e.commandId()).isEqualTo("abc");
        assertThat(e.status()).isEqualTo("ERROR");
        assertThat(e.posicion()).isNull();
        assertThat(e.pasos()).isEqualTo(17345L);
        assertThat(e.codigo()).isEqualTo("FIN_DE_CARRERA");
        assertThat(e.detalle()).isEqualTo("tocó el final");
    }

    @Test
    void llegoTraeLaPosicion() {
        receiver.processMessage(MessageBuilder.withPayload(
                "{\"commandId\":\"abc\",\"status\":\"LLEGO\",\"posicion\":1,\"pasos\":21000}").build());

        ArgumentCaptor<EventoRiel> c = ArgumentCaptor.forClass(EventoRiel.class);
        verify(service).registrarEvento(c.capture());
        assertThat(c.getValue().posicion()).isEqualTo(1);
        assertThat(c.getValue().codigo()).isNull();
    }

    @Test
    void toleraClavesDesconocidas() {
        receiver.processMessage(MessageBuilder.withPayload(
                "{\"commandId\":\"abc\",\"status\":\"ACEPTADO\",\"pasos\":0,\"rssi\":-60}").build());

        verify(service).registrarEvento(any());
    }

    @Test
    void unJsonRotoNoLanzaNiLlamaAlServicio() {
        assertThatCode(() -> receiver.processMessage(MessageBuilder.withPayload("{no es json").build()))
                .doesNotThrowAnyException();

        verify(service, never()).registrarEvento(any());
    }
}
