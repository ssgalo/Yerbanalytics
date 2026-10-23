package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EffectivenessService: espera de cada evaluación, overrides y seguimiento de los tratamientos")
class EffectivenessServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    private RuleOverrides overrides;
    private HistorialRepository history;
    private SectorNeighbors neighbors;
    private EffectivenessService service;

    @BeforeEach
    void setUp() {
        overrides = mock(RuleOverrides.class);
        when(overrides.isEnabled(anyString())).thenReturn(true);
        when(overrides.cooldownMinutes(anyString(), anyString())).thenReturn(Optional.empty());
        history = mock(HistorialRepository.class);
        neighbors = mock(SectorNeighbors.class);
        CatalogoParametrosService catalog = mock(CatalogoParametrosService.class);
        when(catalog.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        service = new EffectivenessService(catalog, overrides, history, neighbors);
    }

    private SectorEntity sector(String state, String severity, Double confidence, Instant diagnosedAt) {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        ZonaEntity zone = RuleContextTestFactory.zonaBasica("MZ-1");
        sector.setZona(zone);
        sector.setDiagnosisEstado(state);
        sector.setDiagnosisSev(severity);
        sector.setDiagnosisConf(confidence);
        sector.setDiagnosisTs(diagnosedAt == null ? null : diagnosedAt.toEpochMilli());
        return sector;
    }

    @Test
    void lasEsperasDeFabricaSonLasDeReglasV2() {
        assertThat(service.latencyMs(EffectivenessKind.IRRIGATION)).isEqualTo(30 * MINUTE);
        assertThat(service.latencyMs(EffectivenessKind.CORRECTIVE_FERTILIZER)).isEqualTo(14 * DAY);
        assertThat(service.latencyMs(EffectivenessKind.FUNGICIDE)).isEqualTo(7 * DAY);
    }

    @Test
    void laEsperaSeMuestraComoEnElHistorial() {
        assertThat(service.latencyLabel(EffectivenessKind.IRRIGATION)).isEqualTo("30 min");
        assertThat(service.latencyLabel(EffectivenessKind.CORRECTIVE_FERTILIZER)).isEqualTo("14 d");
        assertThat(service.latencyLabel(EffectivenessKind.FUNGICIDE)).isEqualTo("7 d");
    }

    @Test
    void elSimuladorAcortaCadaEsperaEnMinutosYCeroLaElimina() {
        when(overrides.cooldownMinutes(EffectivenessService.NAME, EffectivenessService.COOLDOWN_VERIFICATION))
                .thenReturn(Optional.of(0.0));
        when(overrides.cooldownMinutes(EffectivenessService.NAME, EffectivenessService.COOLDOWN_FERTILIZER))
                .thenReturn(Optional.of(5.0));
        when(overrides.cooldownMinutes(EffectivenessService.NAME, EffectivenessService.COOLDOWN_FUNGICIDE))
                .thenReturn(Optional.of(0.0));

        assertThat(service.latencyMs(EffectivenessKind.IRRIGATION)).isZero();
        assertThat(service.latencyMs(EffectivenessKind.CORRECTIVE_FERTILIZER)).isEqualTo(5 * MINUTE);
        assertThat(service.latencyMs(EffectivenessKind.FUNGICIDE)).isZero();
    }

    @Test
    void elDeltaDelRiegoYLaGraciaSalenDelCatalogo() {
        assertThat(service.irrigationDelta()).isEqualTo(8.0);
        assertThat(service.noDiagnosisGraceMs()).isEqualTo(48 * 60 * MINUTE);
    }

    @Test
    void esDemoControllableConSusLlaves() {
        assertThat(service.name()).isEqualTo("EffectivenessService");
        assertThat(service.cooldownKeys()).containsExactlyInAnyOrder("verification", "fertilizer-latency", "fungicide-latency");
        assertThat(service.bypassKeys()).isEmpty();
    }

    @Test
    void elDiagnosticoVigenteTraeEstadoYSeveridad() {
        SectorEntity sector = sector("Clorosis", "Alta", 90.0, NOW.minusSeconds(3600));

        DiagnosisView view = service.diagnosisOf(sector, NOW);

        assertThat(view.valid()).isTrue();
        assertThat(view.state()).isEqualTo("Clorosis");
        assertThat(view.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void unDiagnosticoViejoNoEsVigenteYDiceElPorque() {
        SectorEntity sector = sector("Clorosis", "Alta", 90.0, NOW.minusSeconds(100 * 3600));

        DiagnosisView view = service.diagnosisOf(sector, NOW);

        assertThat(view.valid()).isFalse();
        assertThat(view.reason()).contains("100 h");
    }

    @Test
    void elFitosanitarioAbreSeguimientoConElEstadoPrevioYLosLindantesAfectados() {
        SectorEntity sector = sector("Daño fúngico", "Media", 90.0, NOW.minusSeconds(3600));
        when(neighbors.affectedAround(any(SectorEntity.class), any())).thenReturn(Set.of("MZ-1-044"));

        Optional<FollowUp> followUp = service.followUpFor(sector, Supply.FUNGICIDE, "FungalDamageRule", NOW);

        assertThat(followUp).isPresent();
        assertThat(followUp.get().kind()).isEqualTo(EffectivenessKind.FUNGICIDE);
        assertThat(followUp.get().baseline().state()).isEqualTo("Daño fúngico");
        assertThat(followUp.get().baseline().severity()).isEqualTo(Severity.MODERATE);
        assertThat(followUp.get().baseline().neighbors()).containsExactly("MZ-1-044");
    }

    @Test
    void elFertilizanteDeLaClorosisAbreSeguimientoPeroElDelPlanDeNutricionNo() {
        SectorEntity sector = sector("Clorosis", "Alta", 90.0, NOW.minusSeconds(3600));

        assertThat(service.followUpFor(sector, Supply.FERTILIZER, "ChlorosisNutritionalRule", NOW))
                .hasValueSatisfying(f -> assertThat(f.kind()).isEqualTo(EffectivenessKind.CORRECTIVE_FERTILIZER));
        assertThat(service.followUpFor(sector, Supply.FERTILIZER, "NutritionPlanRule", NOW)).isEmpty();
    }

    @Test
    void unaRachaSeEvaluaUnaSolaVezMientrasHayaUnSeguimientoAbierto() {
        SectorEntity sector = sector("Clorosis", "Alta", 90.0, NOW.minusSeconds(3600));
        when(history.existsBySectorIdAndEfectividadTipoAndEvoShowTrueAndEvoEvaluadoTsIsNull(
                "MZ-1-045", EffectivenessKind.CORRECTIVE_FERTILIZER.name())).thenReturn(true);

        assertThat(service.followUpFor(sector, Supply.FERTILIZER, "ChlorosisNutritionalRule", NOW)).isEmpty();
    }

    @Test
    void sinDiagnosticoAnomaloNoHayNadaQueEvaluar() {
        assertThat(service.followUpFor(sector("Sano", "—", 95.0, NOW.minusSeconds(60)), Supply.FUNGICIDE,
                "OutbreakRule", NOW)).isEmpty();
        assertThat(service.followUpFor(sector("No concluyente", "—", null, NOW.minusSeconds(60)), Supply.FUNGICIDE,
                "OutbreakRule", NOW)).isEmpty();
    }

    @Test
    void unLindanteCuentaComoAfectadoSiTieneElMismoDiagnosticoVigente() {
        SectorEntity sector = sector("Daño fúngico", "Media", 90.0, NOW.minusSeconds(3600));
        SectorEntity same = sector("Daño fúngico", "Baja", 88.0, NOW.minusSeconds(3600));
        SectorEntity stale = sector("Daño fúngico", "Baja", 88.0, NOW.minusSeconds(200 * 3600));
        SectorEntity other = sector("Clorosis", "Baja", 88.0, NOW.minusSeconds(3600));
        when(neighbors.affectedAround(any(SectorEntity.class), any())).thenAnswer(inv -> {
            Predicate<SectorEntity> test = inv.getArgument(1);
            return Set.of(same, stale, other).stream().filter(test).count() == 1 ? Set.of("ok") : Set.of("mal");
        });

        assertThat(service.followUpFor(sector, Supply.FUNGICIDE, "OutbreakRule", NOW))
                .hasValueSatisfying(f -> assertThat(f.baseline().neighbors()).containsExactly("ok"));
    }
}
