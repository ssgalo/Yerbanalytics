package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.model.DiagnosticoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P-D1 · diagnóstico vigente")
class ValidDiagnosisTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    private static DiagnosticoEntity diagnosis(String estado, Double conf, long hoursAgo) {
        DiagnosticoEntity d = new DiagnosticoEntity();
        d.setEstado(estado);
        d.setConf(conf);
        d.setCreadoEn(NOW.minusSeconds(hoursAgo * 3600).toEpochMilli());
        return d;
    }

    @Test
    void recentAndConfidentIsValid() {
        assertThat(ValidDiagnosis.of(diagnosis("Clorosis", 82.0, 3), NOW, 70, 48).valid()).isTrue();
    }

    @Test
    void exactlyAtTheLimitsIsStillValid() {
        assertThat(ValidDiagnosis.of(diagnosis("Clorosis", 70.0, 48), NOW, 70, 48).valid()).isTrue();
    }

    @Test
    void olderThanTheValidityIsNotValid() {
        ValidDiagnosis.Verdict v = ValidDiagnosis.of(diagnosis("Clorosis", 90.0, 60), NOW, 70, 48);
        assertThat(v.valid()).isFalse();
        assertThat(v.reason()).contains("60 h");
    }

    @Test
    void confidenceBelowTheThresholdIsNotValid() {
        ValidDiagnosis.Verdict v = ValidDiagnosis.of(diagnosis("Clorosis", 65.0, 2), NOW, 70, 48);
        assertThat(v.valid()).isFalse();
        assertThat(v.reason()).contains("65 %").contains("70 %");
    }

    @Test
    void inconclusiveNeverCounts() {
        assertThat(ValidDiagnosis.of(diagnosis("No concluyente", null, 1), NOW, 70, 48).valid()).isFalse();
    }

    @Test
    void noDiagnosisIsNotValid() {
        assertThat(ValidDiagnosis.of(null, NOW, 70, 48).valid()).isFalse();
    }

    @Test
    void missingConfidenceIsNotValid() {
        assertThat(ValidDiagnosis.of(diagnosis("Clorosis", null, 1), NOW, 70, 48).valid()).isFalse();
    }

    // --- Con los datos que el sector guarda de su último diagnóstico (lo usan las reglas, que no leen la entidad) ---

    private static ValidDiagnosis.Verdict fromSector(String state, Double conf, Long hoursAgo, double maxAgeHours) {
        Long createdAt = hoursAgo == null ? null : NOW.minusSeconds(hoursAgo * 3600).toEpochMilli();
        return ValidDiagnosis.of(state, conf, createdAt, NOW, 70, maxAgeHours);
    }

    @Test
    void theSectorFieldsGiveTheSameVerdictAsTheEntity() {
        assertThat(fromSector("Daño fúngico", 82.0, 3L, 48).valid()).isTrue();
        assertThat(fromSector("Daño fúngico", 82.0, 60L, 48).reason()).contains("60 h");
        assertThat(fromSector("Daño fúngico", 65.0, 2L, 48).reason()).contains("65 %").contains("70 %");
        assertThat(fromSector("No concluyente", null, 1L, 48).valid()).isFalse();
    }

    @Test
    void aSectorThatNeverHadADiagnosisHasNothingToApplyOn() {
        assertThat(fromSector("Sano", null, null, 48).valid()).isFalse();
        assertThat(fromSector("Sano", null, null, 48).reason()).contains("no tiene diagnóstico");
    }

    @Test
    void theAgeLimitIsTheOneTheCallerPasses() {
        // F-04 mira una ventana de 72 h aunque la vigencia de P-D1 sea de 48.
        assertThat(fromSector("Daño fúngico", 90.0, 60L, 72).valid()).isTrue();
        assertThat(fromSector("Daño fúngico", 90.0, 80L, 72).valid()).isFalse();
    }
}
