package com.yerbanalytics.backend.readings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VaporPressureBand")
class VaporPressureBandTest {

    @ParameterizedTest(name = "{0} kPa es {1}")
    @CsvSource({
            "0.0, SATURATED",
            "0.29, SATURATED",
            "0.3, TRANSITION",
            "0.49, TRANSITION",
            "0.5, COMFORTABLE",
            "1.19, COMFORTABLE",
            "1.2, HIGH_DEMAND",
            "1.99, HIGH_DEMAND",
            "2.0, DRY_HEAT",
            "5.0, DRY_HEAT"
    })
    @DisplayName("los tramos de reglas_v2 §1.4, con el límite inferior incluido")
    void bands(double dpv, VaporPressureBand expected) {
        assertThat(VaporPressureBand.of(dpv)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} se etiqueta «{1}»")
    @CsvSource({
            "SATURATED, aire saturado",
            "TRANSITION, transición",
            "COMFORTABLE, confortable",
            "HIGH_DEMAND, demanda alta",
            "DRY_HEAT, calor seco"
    })
    @DisplayName("cada tramo tiene su etiqueta")
    void labels(VaporPressureBand band, String label) {
        assertThat(band.label()).isEqualTo(label);
    }
}
