package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tarea 1.1: tipos de valor y su formato canónico. */
@DisplayName("ValorParametro / VentanaHoraria")
class ValorParametroTest {

    @Test
    void ventana_parseaYContieneConLimiteInferiorInclusivoYSuperiorExclusivo() {
        VentanaHoraria v = VentanaHoraria.parse("06:00-18:00");

        assertThat(v.desde()).isEqualTo(LocalTime.of(6, 0));
        assertThat(v.hasta()).isEqualTo(LocalTime.of(18, 0));
        assertThat(v.contiene(LocalTime.of(5, 59))).isFalse();
        assertThat(v.contiene(LocalTime.of(6, 0))).isTrue();
        assertThat(v.contiene(LocalTime.of(17, 59))).isTrue();
        assertThat(v.contiene(LocalTime.of(18, 0))).isFalse();
        assertThat(v.contiene(LocalTime.of(18, 30))).isFalse();
    }

    @Test
    void ventana_queCruzaLaMedianoche() {
        VentanaHoraria v = VentanaHoraria.parse("18:00-06:00");

        assertThat(v.contiene(LocalTime.of(2, 0))).isTrue();
        assertThat(v.contiene(LocalTime.of(18, 0))).isTrue();
        assertThat(v.contiene(LocalTime.of(5, 59))).isTrue();
        assertThat(v.contiene(LocalTime.of(6, 0))).isFalse();
        assertThat(v.contiene(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void ventana_formatosInvalidos() {
        for (String malo : new String[]{"25:00-18:00", "06:00", "", "  ", "06:00-", "6:00-18:00",
                "06:00-18:60", "06:00 18:00", "06:00-06:00"}) {
            assertThatThrownBy(() -> VentanaHoraria.parse(malo))
                    .as("'%s'", malo)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HH:mm-HH:mm");
        }
        assertThatThrownBy(() -> VentanaHoraria.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatoCanonico() {
        assertThat(new ValorParametro.Numero(42.0).canonico()).isEqualTo("42");
        assertThat(new ValorParametro.Numero(0.2).canonico()).isEqualTo("0.2");
        assertThat(new ValorParametro.Numero(7.50).canonico()).isEqualTo("7.5");
        assertThat(new ValorParametro.Hora(LocalTime.of(6, 5)).canonico()).isEqualTo("06:05");
        assertThat(VentanaHoraria.parse("06:00-18:00").canonico()).isEqualTo("06:00-18:00");
    }

    @Test
    void hora_parseaEstricto() {
        assertThat(ValorParametro.Hora.parse("06:30").valor()).isEqualTo(LocalTime.of(6, 30));
        assertThatThrownBy(() -> ValorParametro.Hora.parse("24:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HH:mm");
        assertThatThrownBy(() -> ValorParametro.Hora.parse("6:30"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
