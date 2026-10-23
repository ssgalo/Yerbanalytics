package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.model.EtapaPlanRustificacion;
import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Rustificación en curso según el plan de la macro-zona")
class ZonePlanHardeningPhaseTest {

    /** Mediodía del vivero del 22/10/2026. */
    private static final Instant TODAY = Instant.parse("2026-10-22T15:00:00Z");

    private final PlanRustificacionRepository plans = mock(PlanRustificacionRepository.class);
    private final ZonePlanHardeningPhase phase = new ZonePlanHardeningPhase(plans);
    private final ZonaEntity zone = new ZonaEntity();

    ZonePlanHardeningPhaseTest() {
        zone.setId("MZ-1");
    }

    /** Plan de dos etapas de 7 días: termina 14 días después de {@code start}. */
    private void plan(LocalDate start) {
        PlanRustificacionEntity plan = new PlanRustificacionEntity();
        plan.setZonaId("MZ-1");
        plan.setFechaInicio(start);
        plan.getEtapas().add(new EtapaPlanRustificacion(1, 7, "10:00", "13:00"));
        plan.getEtapas().add(new EtapaPlanRustificacion(2, 7, "09:00", "15:00"));
        when(plans.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.of(plan));
    }

    @Test
    void conUnaEtapaRigiendoHoyEstaEnRustificacion() {
        plan(LocalDate.of(2026, 10, 15));
        assertThat(phase.isUnderway(zone, TODAY)).isTrue();
    }

    @Test
    void pasadaLaUltimaEtapaSigueRigiendoLaUltimaYCuenta() {
        plan(LocalDate.of(2026, 8, 1));
        assertThat(phase.isUnderway(zone, TODAY)).isTrue();
    }

    @Test
    void elPrimerDiaDelPlanYaCuenta() {
        plan(LocalDate.of(2026, 10, 22));
        assertThat(phase.isUnderway(zone, TODAY)).isTrue();
    }

    @Test
    void conPlanProgramadoAFuturoNoCuenta() {
        plan(LocalDate.of(2026, 10, 30));
        assertThat(phase.isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void unPlanSinEtapasNoRigeLaMallaYNoCuenta() {
        PlanRustificacionEntity plan = new PlanRustificacionEntity();
        plan.setZonaId("MZ-1");
        plan.setFechaInicio(LocalDate.of(2026, 10, 15));
        when(plans.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.of(plan));
        assertThat(phase.isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void sinPlanVigenteNoCuenta() {
        when(plans.findFirstByZonaIdAndCanceladoEnIsNull("MZ-1")).thenReturn(Optional.empty());
        assertThat(phase.isUnderway(zone, TODAY)).isFalse();
    }
}
