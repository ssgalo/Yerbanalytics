package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.service.NurseryService;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Los dos receptores de telemetría respetan la compuerta y sacan la zona de las dos formas de tópico. */
@DisplayName("TelemetryAdmission en los receptores")
class TelemetryAdmissionTest {

    private static final String JSON = "{\"mac\":\"AA\",\"signal\":-60,\"timestamp\":812,\"metrics\":{\"humSus\":41.0}}";

    private final NurseryService nursery = mock(NurseryService.class);
    private final SecuenciaService secuencias = mock(SecuenciaService.class);
    private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-10-10T15:00:00Z"));

    private static Message<String> mensaje(String topico) {
        return MessageBuilder.withPayload(JSON).setHeader("mqtt_receivedTopic", topico).build();
    }

    @Test
    void laZonaSalePorIgualDeAmbasFormasDeTopico() {
        assertThat(TelemetryAdmission.zoneId("nursery/zone/MZ-2/telemetry")).isEqualTo("MZ-2");
        assertThat(TelemetryAdmission.zoneId("nursery/demo/zone/MZ-2/telemetry")).isEqualTo("MZ-2");
        assertThat(TelemetryAdmission.zoneId("raro")).isNull();
        assertThat(TelemetryAdmission.zoneId(null)).isNull();
    }

    @Test
    void unMensajeSinTopicoSeDescartaComoAntes() {
        MqttTelemetryReceiver receiver = new MqttTelemetryReceiver(nursery, TelemetryAdmission.ALL);

        receiver.processMessage(MessageBuilder.withPayload(JSON).build());

        verify(nursery, never()).updateTelemetry(any(), any());
    }

    @Test
    void laIngestaAdmiteElTopicoRealYTomaLaZonaDelTopicoDeDemo() {
        TelemetryAdmission soloDemo = t -> t.startsWith("nursery/demo/");
        MqttTelemetryReceiver receiver = new MqttTelemetryReceiver(nursery, soloDemo);

        receiver.processMessage(mensaje("nursery/zone/MZ-1/telemetry"));
        verify(nursery, never()).updateTelemetry(any(), any());

        receiver.processMessage(mensaje("nursery/demo/zone/MZ-3/telemetry"));
        verify(nursery).updateTelemetry(eq("MZ-3"), any());
    }

    @Test
    void laLecturaDeDemoExpoTambienRespetaLaCompuerta() {
        TelemetryAdmission soloReal = t -> !t.startsWith("nursery/demo/");
        LecturaZonaReceiver receiver = new LecturaZonaReceiver(secuencias, reloj, soloReal);

        receiver.processMessage(mensaje("nursery/demo/zone/MZ-1/telemetry"));
        verify(secuencias, never()).registrarTelemetria(any(), any(), anyLong());

        receiver.processMessage(mensaje("nursery/zone/MZ-1/telemetry"));
        verify(secuencias).registrarTelemetria(eq("MZ-1"), any(), eq(reloj.millis()));
    }

    @Test
    void laLecturaDeDemoExpoSacaLaZonaDelTopicoDeDemo() {
        new LecturaZonaReceiver(secuencias, reloj, TelemetryAdmission.ALL)
                .processMessage(mensaje("nursery/demo/zone/MZ-4/telemetry"));

        verify(secuencias).registrarTelemetria(eq("MZ-4"), any(), eq(reloj.millis()));
    }
}
