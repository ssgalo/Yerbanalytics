package com.yerbanalytics.backend.biotic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tipo de daño biótico según el estado del diagnóstico (D-1)")
class BioticKindTest {

    @Test
    void danoFungicoEsHongo() {
        assertThat(BioticKind.of("Daño fúngico")).contains(BioticKind.FUNGAL);
    }

    @Test
    void plagaFoliarYAcaroSonPlaga() {
        assertThat(BioticKind.of("Plaga foliar")).contains(BioticKind.PEST);
        assertThat(BioticKind.of("Ácaro")).contains(BioticKind.PEST);
    }

    @Test
    void elDanoBioticoGenericoNoEsNingunoDeLosDos() {
        assertThat(BioticKind.of("Daño biótico")).isEmpty();
    }

    @Test
    void losDemasEstadosNoSonBioticos() {
        assertThat(BioticKind.of("Sano")).isEmpty();
        assertThat(BioticKind.of("Clorosis")).isEmpty();
        assertThat(BioticKind.of("No concluyente")).isEmpty();
        assertThat(BioticKind.of(null)).isEmpty();
    }

    @Test
    void diceDeQueEstadosSeTrata() {
        assertThat(BioticKind.FUNGAL.matches("Daño fúngico")).isTrue();
        assertThat(BioticKind.FUNGAL.matches("Plaga foliar")).isFalse();
        assertThat(BioticKind.PEST.matches("Ácaro")).isTrue();
    }
}
