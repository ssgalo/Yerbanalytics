package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 2.1: la ventana cerrada al minuto (la lectura de las 18:00 todavía entra). */
@DisplayName("VentanaHoraria.contieneHastaElMinuto")
class VentanaHorariaTest {

    private static final VentanaHoraria DIURNA = VentanaHoraria.parse("06:00-18:00");
    private static final VentanaHoraria NOCTURNA = VentanaHoraria.parse("22:00-06:00");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "05:59:59, false",
            "06:00:00, true",
            "17:59:59, true",
            "18:00:00, true",
            "18:00:59, true",
            "18:01:00, false"})
    void ventanaDiurna(String hora, boolean adentro) {
        assertThat(DIURNA.contieneHastaElMinuto(LocalTime.parse(hora))).isEqualTo(adentro);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "21:59:59, false",
            "22:00:00, true",
            "03:00:00, true",
            "06:00:00, true",
            "06:00:30, true",
            "06:01:00, false"})
    void ventanaQueCruzaLaMedianoche(String hora, boolean adentro) {
        assertThat(NOCTURNA.contieneHastaElMinuto(LocalTime.parse(hora))).isEqualTo(adentro);
    }

    @Test
    void contieneSigueSiendoSemiabierta() {
        assertThat(DIURNA.contiene(LocalTime.of(18, 0))).isFalse();
    }
}
