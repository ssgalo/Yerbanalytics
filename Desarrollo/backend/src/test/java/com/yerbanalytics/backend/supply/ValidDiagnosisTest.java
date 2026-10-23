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
}
