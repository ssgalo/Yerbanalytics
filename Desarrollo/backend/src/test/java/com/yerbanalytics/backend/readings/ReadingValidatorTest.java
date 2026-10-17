package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload.MetricsPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** S-03: el rango físico de cada métrica, con los bordes incluidos y los valores del catálogo. */
@DisplayName("ReadingValidator - rango físico (S-03)")
class ReadingValidatorTest {

    private static final ParametrosVigentes FABRICA = ReglaTestSupport.fabrica();

    private static Set<String> invalidas(String clave, double valor) {
        return ReadingValidator.invalidMetrics(ReadingMetrics.of(Map.of(clave, valor)), FABRICA);
    }

    @ParameterizedTest(name = "{0} = {1} → válida: {2}")
    @CsvSource({
            // humedad: 0–100 % (sustrato y ambiente)
            "humSus,   112.0, false",
            "humSus,   100.0, true",
            "humSus,     0.0, true",
            "humSus,    -0.1, false",
            "humAmb,   100.1, false",
            "humAmb,    55.0, true",
            // temperatura: −10…60 °C (aire y sustrato)
            "temp,     -10.0, true",
            "temp,     -10.1, false",
            "temp,      60.0, true",
            "temp,      60.1, false",
            "tempSuelo, 60.1, false",
            "tempSuelo, 18.0, true",
            // pH: 3–9
            "phSuelo,    2.9, false",
            "phSuelo,    3.0, true",
            "phSuelo,    9.0, true",
            "phSuelo,    9.1, false",
            "phSuelo,   11.0, false",
            // CE: 0–10 dS/m, ya convertida
            "ce,        10.0, true",
            "ce,        10.01, false",
            "ce,        -0.1, false",
            // luz: 0–100 %
            "uv,       100.0, true",
            "uv,       100.1, false",
            "uv,        -1.0, false",
            // N, P y K no tienen rango físico en la v2: nunca se invalidan
            "n,      99999.0, true",
            "p,         -5.0, true",
            "k,      99999.0, true",
    })
    void rangoPorMetrica(String clave, double valor, boolean valida) {
        assertThat(invalidas(clave, valor).isEmpty()).isEqualTo(valida);
    }

    @Test
    @DisplayName("la CE se valida ya convertida: 12 000 µS/cm son 12 dS/m y exceden el máximo de 10")
    void ceConvertida() {
        ReadingMetrics m = ReadingMetrics.from(new MetricsPayload(null, null, null, null, null, 12_000.0, null, null, null, null));

        assertThat(ReadingValidator.invalidMetrics(m, FABRICA)).containsExactly("ce");
        // 9 500 µS/cm son 9,5 dS/m: válida (si se validara en µS/cm daría inválida).
        ReadingMetrics ok = ReadingMetrics.from(new MetricsPayload(null, null, null, null, null, 9_500.0, null, null, null, null));
        assertThat(ReadingValidator.invalidMetrics(ok, FABRICA)).isEmpty();
    }

    @Test
    @DisplayName("un valor no finito (NaN, infinito) es inválido")
    void noFinito() {
        assertThat(invalidas("temp", Double.NaN)).containsExactly("temp");
        assertThat(invalidas("humSus", Double.POSITIVE_INFINITY)).containsExactly("humSus");
    }

    @Test
    @DisplayName("devuelve todas las métricas inválidas de la lectura y respeta las válidas")
    void variasInvalidas() {
        ReadingMetrics m = ReadingMetrics.of(Map.of("humSus", 112.0, "temp", 22.0, "phSuelo", 11.0, "n", 40.0));

        assertThat(ReadingValidator.invalidMetrics(m, FABRICA)).containsExactlyInAnyOrder("humSus", "phSuelo");
    }

    @Test
    @DisplayName("usa los rangos vigentes del catálogo, no los de fábrica")
    void rangosEditados() {
        ParametrosVigentes angosto = ReglaTestSupport.con(Map.of(ParametrosSeguridad.RANGO_HUM_MAX, "90"));

        assertThat(ReadingValidator.invalidMetrics(ReadingMetrics.of(Map.of("humSus", 95.0)), angosto))
                .containsExactly("humSus");
        assertThat(ReadingValidator.invalidMetrics(ReadingMetrics.of(Map.of("humSus", 95.0)), FABRICA)).isEmpty();
    }

    @Test
    @DisplayName("una lectura vacía no tiene métricas inválidas")
    void vacia() {
        assertThat(ReadingValidator.invalidMetrics(ReadingMetrics.of(Map.of()), FABRICA)).isEmpty();
    }
}
