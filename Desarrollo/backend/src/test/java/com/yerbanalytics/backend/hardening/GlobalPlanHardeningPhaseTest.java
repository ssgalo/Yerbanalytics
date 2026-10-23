package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Rustificación en curso según el plan global (hasta que llegue el plan por macro-zona)")
class GlobalPlanHardeningPhaseTest {

    /** Mediodía del vivero del 22/10/2026. */
    private static final Instant TODAY = Instant.parse("2026-10-22T15:00:00Z");

    private final RustificacionEtapaRepository stages = mock(RustificacionEtapaRepository.class);
    private final ZonaEntity zone = new ZonaEntity();

    private GlobalPlanHardeningPhase phase(String sowingDate) {
        when(stages.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                new RustificacionEtapaEntity(1, 1, 10, 100),
                new RustificacionEtapaEntity(2, 11, 20, 70),
                new RustificacionEtapaEntity(3, 21, 30, 40)));
        return new GlobalPlanHardeningPhase(stages, sowingDate);
    }

    @Test
    void conElPlanEnCursoEstaEnRustificacion() {
        // Sembrado el 15/10: hoy es el día 8, en la primera etapa.
        assertThat(phase("2026-10-15").isUnderway(zone, TODAY)).isTrue();
        // Sembrado el 02/10: hoy es el día 21, en la última.
        assertThat(phase("2026-10-02").isUnderway(zone, TODAY)).isTrue();
    }

    @Test
    void conElPlanFinalizadoTodaviaCuenta() {
        // Sembrado el 01/09: el plan terminó el día 30; la v2 lo cuenta como rustificación («en curso o finalizado»).
        assertThat(phase("2026-09-01").isUnderway(zone, TODAY)).isTrue();
    }

    @Test
    void antesDeSembrarNoHayRustificacion() {
        assertThat(phase("2026-10-30").isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void sinFechaDeSiembraNoSePuedeSaberYNoCuenta() {
        GlobalPlanHardeningPhase phase = phase("");

        assertThat(phase.isUnderway(zone, TODAY)).isFalse();
        assertThat(new GlobalPlanHardeningPhase(stages, null).isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void sinEtapasNoHayPlan() {
        when(stages.findAllByOrderByOrdenAsc()).thenReturn(List.of());

        assertThat(new GlobalPlanHardeningPhase(stages, "2026-10-15").isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void unaFechaMalEscritaNoRompeAlMotor() {
        assertThat(phase("pronto").isUnderway(zone, TODAY)).isFalse();
    }

    @Test
    void sinFechaNoConsultaLaBase() {
        RustificacionEtapaRepository untouched = mock(RustificacionEtapaRepository.class);

        new GlobalPlanHardeningPhase(untouched, "").isUnderway(zone, TODAY);

        verifyNoInteractions(untouched);
    }
}
