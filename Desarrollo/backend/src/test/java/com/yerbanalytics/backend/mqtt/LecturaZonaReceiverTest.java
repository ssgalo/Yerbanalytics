package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.service.NurseryService;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Segunda suscripción a la telemetría, sólo para las secuencias (design add-secuencias-demo-expo §2.2):
 * sella la hora de recepción y avisa al servicio de secuencias. No toca el camino del motor.
 */
@DisplayName("LecturaZonaReceiver")
class LecturaZonaReceiverTest {

    private final SecuenciaService service = mock(SecuenciaService.class);
    private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-10-10T15:00:00Z"));
    private final LecturaZonaReceiver receiver = new LecturaZonaReceiver(service, reloj);

    private static Message<String> mensaje(String json, String topico) {
        return MessageBuilder.withPayload(json).setHeader("mqtt_receivedTopic", topico).build();
    }

    @Test
    void laTelemetriaLlegaConLaZonaDelTopicoYLaHoraDeRecepcionDelReloj() {
        receiver.processMessage(mensaje(
                "{\"mac\":\"AA:BB\",\"signal\":-60,\"timestamp\":812,"
                        + "\"metrics\":{\"humSus\":41.0,\"ce\":1200.0,\"salinidad\":9}}",
                "nursery/zone/MZ-3/telemetry"));

        ArgumentCaptor<MqttTelemetryPayload> c = ArgumentCaptor.forClass(MqttTelemetryPayload.class);
        verify(service).registrarTelemetria(eq("MZ-3"), c.capture(), eq(reloj.millis()));
        assertThat(c.getValue().metrics().humSus()).isEqualTo(41.0);
        assertThat(c.getValue().metrics().ce()).isEqualTo(1200.0);
    }

    @Test
    void unJsonRotoNoLanzaNiLlamaAlServicio() {
        assertThatCode(() -> receiver.processMessage(mensaje("{no es json", "nursery/zone/MZ-1/telemetry")))
                .doesNotThrowAnyException();

        verify(service, never()).registrarTelemetria(anyString(), any(), anyLong());
    }

    @Test
    void sinTopicoNoSabeDeQueZonaEsYDescarta() {
        assertThatCode(() -> receiver.processMessage(
                MessageBuilder.withPayload("{\"metrics\":{}}").build())).doesNotThrowAnyException();

        verify(service, never()).registrarTelemetria(anyString(), any(), anyLong());
    }

    @Test
    void nuncaDependeDeNurseryService() {
        for (Constructor<?> c : LecturaZonaReceiver.class.getDeclaredConstructors()) {
            assertThat(Arrays.asList(c.getParameterTypes())).doesNotContain(NurseryService.class);
        }
    }
}
