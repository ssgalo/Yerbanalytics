package com.yerbanalytics.backend.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Severidad del diagnóstico (leve / moderada / alta)")
class SeverityTest {

    @Test
    void bajaEsLeveMediaEsModeradaYAltaEsAlta() {
        assertThat(Severity.parse("Baja")).contains(Severity.MILD);
        assertThat(Severity.parse("Media")).contains(Severity.MODERATE);
        assertThat(Severity.parse("Alta")).contains(Severity.HIGH);
    }

    @Test
    void moderadaOAltaDisparanElTratamiento() {
        assertThat(Severity.MILD.isModerateOrHigher()).isFalse();
        assertThat(Severity.MODERATE.isModerateOrHigher()).isTrue();
        assertThat(Severity.HIGH.isModerateOrHigher()).isTrue();
    }

    @Test
    void sinSeveridadNoHayNivel() {
        assertThat(Severity.parse("—")).isEmpty();
        assertThat(Severity.parse(null)).isEmpty();
        assertThat(Severity.parse("Rara")).isEmpty();
    }
}
