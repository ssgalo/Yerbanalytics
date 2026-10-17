package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload.MetricsPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReadingMetrics - las métricas de una lectura en unidades canónicas")
class ReadingMetricsTest {

    @Test
    @DisplayName("un payload sin métricas da una lectura vacía")
    void sinMetricas() {
        assertThat(ReadingMetrics.from(null).isEmpty()).isTrue();
        assertThat(ReadingMetrics.from(new MetricsPayload(null, null, null, null, null, null, null, null, null, null))
                .isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sólo trae las métricas presentes, por su clave del contrato")
    void soloLasPresentes() {
        ReadingMetrics m = ReadingMetrics.from(new MetricsPayload(41.0, null, 22.5, null, 80.0, null, 5.4, null, null, null));

        assertThat(m.keys()).containsExactlyInAnyOrder("humSus", "temp", "uv", "phSuelo");
        assertThat(m.get("humSus")).isEqualTo(41.0);
        assertThat(m.get("phSuelo")).isEqualTo(5.4);
        assertThat(m.has("humAmb")).isFalse();
        assertThat(m.get("humAmb")).isNull();
    }

    @Test
    @DisplayName("la conductividad se convierte de µS/cm (contrato) a dS/m (plataforma) una sola vez")
    void ceEnDsPorM() {
        ReadingMetrics m = ReadingMetrics.from(new MetricsPayload(null, null, null, null, null, 12_000.0, null, null, null, null));

        assertThat(m.get("ce")).isEqualTo(12.0);
    }

    @Test
    @DisplayName("without() devuelve otra lectura sin esas claves y no toca la original")
    void without() {
        ReadingMetrics m = ReadingMetrics.from(new MetricsPayload(41.0, 60.0, 22.5, null, null, null, null, null, null, null));

        ReadingMetrics sinHumedad = m.without(Set.of("humSus", "inexistente"));

        assertThat(sinHumedad.keys()).containsExactlyInAnyOrder("humAmb", "temp");
        assertThat(m.keys()).containsExactlyInAnyOrder("humSus", "humAmb", "temp");
    }
}
