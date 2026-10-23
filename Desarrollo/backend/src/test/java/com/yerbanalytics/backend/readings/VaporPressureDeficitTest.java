package com.yerbanalytics.backend.readings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VaporPressureDeficit")
class VaporPressureDeficitTest {

    @ParameterizedTest(name = "{0} °C y {1} % dan {2} kPa")
    @CsvSource({
            "25.0, 60.0, 1.27",
            "25.0, 100.0, 0.0",
            "25.0, 0.0, 3.17",
            "38.0, 25.0, 4.97",
            "10.0, 90.0, 0.12"
    })
    @DisplayName("valores de referencia, redondeados a 0,01 kPa")
    void referenceValues(double temp, double hum, double expected) {
        assertThat(VaporPressureDeficit.calculate(temp, hum)).isNotNull();
        assertThat(VaporPressureDeficit.round(VaporPressureDeficit.calculate(temp, hum))).isEqualTo(expected);
    }

    @Test
    @DisplayName("el cálculo interno no redondea")
    void calculateDoesNotRound() {
        assertThat(VaporPressureDeficit.calculate(25.0, 60.0)).isNotEqualTo(1.27).isBetween(1.26, 1.28);
    }

    @Test
    @DisplayName("sin temperatura o sin humedad no hay DPV")
    void missingInputs() {
        assertThat(VaporPressureDeficit.calculate(null, 60.0)).isNull();
        assertThat(VaporPressureDeficit.calculate(25.0, null)).isNull();
        assertThat(VaporPressureDeficit.calculate(null, null)).isNull();
    }

    @ParameterizedTest(name = "humedad {0} % fuera de rango")
    @CsvSource({"130.0", "-1.0", "100.01"})
    @DisplayName("una humedad fuera de 0-100 no se calcula")
    void humidityOutOfRange(double hum) {
        assertThat(VaporPressureDeficit.calculate(25.0, hum)).isNull();
    }

    @Test
    @DisplayName("entradas no finitas o la singularidad de la fórmula no dan DPV")
    void degenerateInputs() {
        assertThat(VaporPressureDeficit.calculate(Double.NaN, 60.0)).isNull();
        assertThat(VaporPressureDeficit.calculate(25.0, Double.NaN)).isNull();
        assertThat(VaporPressureDeficit.calculate(Double.POSITIVE_INFINITY, 60.0)).isNull();
        assertThat(VaporPressureDeficit.calculate(-237.3, 60.0)).isNull();
    }
}
