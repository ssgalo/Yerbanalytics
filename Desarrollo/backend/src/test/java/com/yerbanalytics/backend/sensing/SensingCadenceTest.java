package com.yerbanalytics.backend.sensing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensingCadence")
class SensingCadenceTest {

    @Test
    void intervaloNuloSeLeeComo240() {
        assertThat(SensingCadence.intervalMinutes(null)).isEqualTo(240);
    }

    @Test
    void intervaloFueraDeRangoSeAcota() {
        assertThat(SensingCadence.intervalMinutes(30)).isEqualTo(60);
        assertThat(SensingCadence.intervalMinutes(-5)).isEqualTo(60);
        assertThat(SensingCadence.intervalMinutes(600)).isEqualTo(360);
    }

    @Test
    void intervaloEnRangoQuedaIgual() {
        assertThat(SensingCadence.intervalMinutes(60)).isEqualTo(60);
        assertThat(SensingCadence.intervalMinutes(240)).isEqualTo(240);
        assertThat(SensingCadence.intervalMinutes(360)).isEqualTo(360);
    }

    @Test
    @DisplayName("piso 90 s, pulso de 240 min y ventana de 180 s: 14 580 s")
    void umbralPorDefecto() {
        assertThat(SensingCadence.staleThresholdSeconds(90, 240, 180)).isEqualTo(14_580.0);
    }

    @Test
    @DisplayName("piso 90 s, pulso de 120 min y ventana de 180 s: 7 380 s")
    void umbralCon120() {
        assertThat(SensingCadence.staleThresholdSeconds(90, 120, 180)).isEqualTo(7_380.0);
    }

    @Test
    void elPisoDominaSiEsMayor() {
        assertThat(SensingCadence.staleThresholdSeconds(20_000, 60, 180)).isEqualTo(20_000.0);
    }
}
