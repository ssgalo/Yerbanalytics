package com.yerbanalytics.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ZonaEntity.setRaw - escribir una métrica por su clave del contrato")
class ZonaEntityRawTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"humSus", "humAmb", "temp", "tempSuelo", "uv", "ce", "phSuelo", "n", "p", "k"})
    @DisplayName("lo que se escribe por clave se lee igual con raw()")
    void escribeYLee(String clave) {
        ZonaEntity zona = new ZonaEntity();

        zona.setRaw(clave, 12.5);

        assertThat(zona.raw(clave)).isEqualTo(12.5);
    }

    @Test
    @DisplayName("escribe en el campo correcto de cada métrica")
    void camposPorMetrica() {
        ZonaEntity zona = new ZonaEntity();

        zona.setRaw("humSus", 1.0);
        zona.setRaw("tempSuelo", 2.0);
        zona.setRaw("ce", 3.0);
        zona.setRaw("phSuelo", 4.0);

        assertThat(zona.getHumSusRaw()).isEqualTo(1.0);
        assertThat(zona.getTempSueloRaw()).isEqualTo(2.0);
        assertThat(zona.getCeRaw()).isEqualTo(3.0);
        assertThat(zona.getPhSueloRaw()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("una clave desconocida es un error de programación, no se ignora en silencio")
    void claveDesconocida() {
        assertThatThrownBy(() -> new ZonaEntity().setRaw("salinidad", 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salinidad");
    }
}
