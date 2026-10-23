package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.dto.NutritionPlan;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan.Change;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.exception.InvalidNutritionIntervalException;
import com.yerbanalytics.backend.exception.NutritionZoneNotFoundException;
import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.supply.ApplicationOutcome;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.NOW;
import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.WITH_PUMP;
import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.sectors;
import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NutritionPlanService · el plan de cada macro-zona")
class NutritionPlanServiceTest {

    private static final String RULE = NutritionPlanRule.NAME;

    private ApplicationService applicationService;
    private ApplicationRepository applications;
    private PendingApplicationRepository pending;
    private ZonaRepository zones;
    private HistorialService history;
    private NutritionPlanService service;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        applicationService = mock(ApplicationService.class);
        applications = mock(ApplicationRepository.class);
        pending = mock(PendingApplicationRepository.class);
        zones = mock(ZonaRepository.class);
        history = mock(HistorialService.class);
        CatalogoParametrosService catalog = mock(CatalogoParametrosService.class);
        when(catalog.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        service = new NutritionPlanService(applicationService, applications, pending, zones, history, catalog);

        zone = zone("MZ-2");
        when(zones.findById("MZ-2")).thenReturn(Optional.of(zone));
        when(applicationService.request(any(), eq(Supply.FERTILIZER), anyString(), eq(false)))
                .thenReturn(ApplicationOutcome.applied());
        when(applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                anyString(), any(), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
    }

    private ZoneRuleContext ctx(List<SectorEntity> sectors) {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        return new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);
    }

    private static ApplicationEntity applicationAt(Instant ts) {
        return new ApplicationEntity("a", "MZ-2-001", "MZ-2", Supply.FERTILIZER, 10, ts.toEpochMilli(), RULE);
    }

    // --- intervalo ---

    @Test
    void sinIntervaloPropioRigeElDelCatalogo() {
        assertThat(service.intervalDays(zone)).isEqualTo(7);
    }

    @Test
    void elIntervaloPropioDeLaZonaManda() {
        zone.setNutritionPlanIntervalDays(14);

        assertThat(service.intervalDays(zone)).isEqualTo(14);
    }

    @Test
    void fijarElIntervaloLoGuardaYDejaConstanciaDeQuienLoCambio() {
        NutritionPlan result = service.setInterval("MZ-2", 14, "Ingeniero Agrónomo");

        assertThat(zone.getNutritionPlanIntervalDays()).isEqualTo(14);
        assertThat(result.intervalDays()).isEqualTo(14);
        assertThat(result.customInterval()).isTrue();
        verify(zones).save(zone);
        verify(history).registrarConfiguracion(eq("Ingeniero Agrónomo"), org.mockito.ArgumentMatchers.contains("7 → 14"));
    }

    @Test
    void fijarElMismoIntervaloNoDejaConstanciaDeUnCambio() {
        service.setInterval("MZ-2", 7, "Ingeniero Agrónomo");

        verify(history, never()).registrarConfiguracion(anyString(), anyString());
    }

    @Test
    void losLimitesDelRangoSeAceptan() {
        assertThat(service.setInterval("MZ-2", 3, "u").intervalDays()).isEqualTo(3);
        assertThat(service.setInterval("MZ-2", 30, "u").intervalDays()).isEqualTo(30);
    }

    @Test
    void dosDiasSeRechaza() {
        assertThatThrownBy(() -> service.setInterval("MZ-2", 2, "u"))
                .isInstanceOf(InvalidNutritionIntervalException.class)
                .hasMessageContaining("entre 3 y 30");
        assertThat(zone.getNutritionPlanIntervalDays()).isNull();
        verify(zones, never()).save(any());
    }

    @Test
    void treintaYUnDiasSeRechazaYNoSeRecorta() {
        assertThatThrownBy(() -> service.setInterval("MZ-2", 31, "u")).isInstanceOf(InvalidNutritionIntervalException.class);
    }

    @Test
    void sinIntervaloSeRechaza() {
        assertThatThrownBy(() -> service.setInterval("MZ-2", null, "u")).isInstanceOf(InvalidNutritionIntervalException.class);
    }

    @Test
    void unaZonaInexistenteSeRechaza() {
        assertThatThrownBy(() -> service.setInterval("MZ-9", 7, "u")).isInstanceOf(NutritionZoneNotFoundException.class);
    }

    // --- estado ---

    @Test
    void elEstadoSinTandaNoConsultaLasAplicacionesPendientes() {
        NutritionPlanFacts facts = service.facts(zone);

        assertThat(facts.batchOpen()).isFalse();
        assertThat(facts.pendingInBatch()).isZero();
        verify(pending, never()).countByZoneIdAndSupplyAndRuleName(anyString(), any(), anyString());
    }

    @Test
    void conLaTandaAbiertaCuentaLasAplicacionesDelPlanQueEsperan() {
        zone.setNutritionBatchStartTs(NOW.minusSeconds(3600).toEpochMilli());
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(12L);

        NutritionPlanFacts facts = service.facts(zone);

        assertThat(facts.batchOpen()).isTrue();
        assertThat(facts.pendingInBatch()).isEqualTo(12);
    }

    @Test
    void laUltimaFertilizacionEsLaDeCualquierSectorDeLaZona() {
        when(applications.findFirstByZoneIdAndSupplyOrderByTsDesc("MZ-2", Supply.FERTILIZER))
                .thenReturn(Optional.of(applicationAt(NOW.minus(Duration.ofDays(2)))));

        assertThat(service.facts(zone).lastFertilization()).isEqualTo(NOW.minus(Duration.ofDays(2)));
    }

    @Test
    void elEstadoParaElDashboardMuestraLaProximaAplicacion() {
        Instant last = NOW.minus(Duration.ofDays(2));
        zone.setLastPlanApplicationTs(last.toEpochMilli());

        NutritionPlan status = service.status(zone);

        assertThat(status.intervalDays()).isEqualTo(7);
        assertThat(status.customInterval()).isFalse();
        assertThat(status.lastApplicationTs()).isEqualTo(last.toEpochMilli());
        assertThat(status.nextDueTs()).isEqualTo(last.plus(Duration.ofDays(7)).toEpochMilli());
        assertThat(status.batchOpen()).isFalse();
        assertThat(status.advanced()).isFalse();
    }

    @Test
    void unaZonaSinHistorialNoTieneFechaDeProximaAplicacionPorqueVenceYa() {
        NutritionPlan status = service.status(zone);

        assertThat(status.lastApplicationTs()).isNull();
        assertThat(status.nextDueTs()).isNull();
    }

    @Test
    void conLaTandaEnCursoNoHayProximaAplicacion() {
        zone.setLastPlanApplicationTs(NOW.minus(Duration.ofDays(8)).toEpochMilli());
        zone.setNutritionBatchStartTs(NOW.toEpochMilli());

        NutritionPlan status = service.status(zone);

        assertThat(status.batchOpen()).isTrue();
        assertThat(status.nextDueTs()).isNull();
        assertThat(status.advanced()).isFalse();
    }

    @Test
    void unPlanAdelantadoMuestraElAdelantamientoComoProximaAplicacion() {
        zone.setLastPlanApplicationTs(NOW.minus(Duration.ofDays(2)).toEpochMilli());
        zone.setNutritionPlanDueTs(NOW.toEpochMilli());

        NutritionPlan status = service.status(zone);

        assertThat(status.advanced()).isTrue();
        assertThat(status.nextDueTs()).isEqualTo(NOW.toEpochMilli());
    }

    // --- START ---

    @Test
    void iniciarElPlanPideFertilizanteParaLos100SectoresDeLaZona() {
        List<SectorEntity> all = sectors(zone, 100);

        service.apply(Change.START, ctx(all), RULE);

        verify(applicationService, times(100)).request(any(), eq(Supply.FERTILIZER), eq(RULE), eq(false));
    }

    @Test
    void sinSectoresEsperandoLaTandaSeCierraEnElMismoCicloConLaHoraDeLaUltimaAplicacion() {
        Instant lastApplied = NOW.plusSeconds(120);
        when(applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                "MZ-2", Supply.FERTILIZER, RULE, NOW.toEpochMilli()))
                .thenReturn(Optional.of(applicationAt(lastApplied)));
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(0L);

        service.apply(Change.START, ctx(sectors(zone, 3)), RULE);

        assertThat(zone.getNutritionBatchStartTs()).isNull();
        assertThat(zone.getLastPlanApplicationTs()).isEqualTo(lastApplied.toEpochMilli());
        assertThat(zone.getNutritionPlanDueTs()).isNull();
    }

    @Test
    void conUnSectorDiferidoLaTandaQuedaAbiertaYElIntervaloNoCorreTodavia() {
        List<SectorEntity> all = sectors(zone, 100);
        when(applicationService.request(eq(all.get(44)), eq(Supply.FERTILIZER), anyString(), eq(false)))
                .thenReturn(ApplicationOutcome.deferred("P-D5: humedad de sustrato 40 %"));
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(1L);

        service.apply(Change.START, ctx(all), RULE);

        assertThat(zone.getNutritionBatchStartTs()).isEqualTo(NOW.toEpochMilli());
        assertThat(zone.getLastPlanApplicationTs()).isNull();
        verify(zones).save(zone);
    }

    @Test
    void siTodosSeRechazanLaTandaSeCierraConLaHoraDeLaDecision() {
        when(applicationService.request(any(), eq(Supply.FERTILIZER), anyString(), eq(false)))
                .thenReturn(ApplicationOutcome.rejected("el tanque de fertilizante no está registrado en MZ-2"));
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(0L);

        service.apply(Change.START, ctx(sectors(zone, 3)), RULE);

        assertThat(zone.getNutritionBatchStartTs()).isNull();
        assertThat(zone.getLastPlanApplicationTs()).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void unSectorSinBombaQuedaFueraDeLaTandaConSuMotivoEnElHistorial() {
        List<SectorEntity> all = sectors(zone, 3);
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        all.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        hardware.put(all.get(1).getId(), HardwareCoverage.NONE);
        ZoneRuleContext ctx = new ZoneRuleContext(zone, all, NOW, hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);

        service.apply(Change.START, ctx, RULE);

        verify(applicationService, times(2)).request(any(), eq(Supply.FERTILIZER), eq(RULE), eq(false));
        verify(applicationService, never()).request(eq(all.get(1)), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(history).registrarInaccion(eq(all.get(1)), eq(ActionType.NOOP_INFO), eq(RULE),
                org.mockito.ArgumentMatchers.contains("bomba"));
    }

    @Test
    void iniciarConLaTandaAbiertaNoVuelveAPedirNada() {
        zone.setNutritionBatchStartTs(NOW.minusSeconds(60).toEpochMilli());

        service.apply(Change.START, ctx(sectors(zone, 100)), RULE);

        verify(applicationService, never()).request(any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void iniciarLaTandaConsumeElAdelantamiento() {
        zone.setNutritionPlanDueTs(NOW.toEpochMilli());
        when(pending.countByZoneIdAndSupplyAndRuleName(anyString(), any(), anyString())).thenReturn(5L);

        service.apply(Change.START, ctx(sectors(zone, 3)), RULE);

        assertThat(zone.getNutritionPlanDueTs()).isNull();
        assertThat(zone.getNutritionBatchStartTs()).isNotNull();
    }

    // --- ADVANCE ---

    @Test
    void adelantarVenceElPlanAhora() {
        service.apply(Change.ADVANCE, ctx(sectors(zone, 3)), LowEcRule.NAME);

        assertThat(zone.getNutritionPlanDueTs()).isEqualTo(NOW.toEpochMilli());
        verify(zones).save(zone);
    }

    @Test
    void adelantarDosVecesNoMueveLaMarca() {
        zone.setNutritionPlanDueTs(NOW.minusSeconds(3600).toEpochMilli());

        service.apply(Change.ADVANCE, ctx(sectors(zone, 3)), LowEcRule.NAME);

        assertThat(zone.getNutritionPlanDueTs()).isEqualTo(NOW.minusSeconds(3600).toEpochMilli());
        verify(zones, never()).save(any());
    }

    @Test
    void conLaTandaEnCursoNoHayNadaQueAdelantar() {
        zone.setNutritionBatchStartTs(NOW.minusSeconds(3600).toEpochMilli());

        service.apply(Change.ADVANCE, ctx(sectors(zone, 3)), LowEcRule.NAME);

        assertThat(zone.getNutritionPlanDueTs()).isNull();
    }

    // --- COMPLETE ---

    @Test
    void completarCierraLaTandaConLaHoraDeLaUltimaAplicacionDelPlan() {
        Instant batchStart = NOW.minus(Duration.ofHours(20));
        Instant lastApplied = NOW.minus(Duration.ofHours(2));
        zone.setNutritionBatchStartTs(batchStart.toEpochMilli());
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(0L);
        when(applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                "MZ-2", Supply.FERTILIZER, RULE, batchStart.toEpochMilli()))
                .thenReturn(Optional.of(applicationAt(lastApplied)));

        service.apply(Change.COMPLETE, ctx(sectors(zone, 3)), RULE);

        assertThat(zone.getNutritionBatchStartTs()).isNull();
        assertThat(zone.getLastPlanApplicationTs()).isEqualTo(lastApplied.toEpochMilli());
    }

    @Test
    void completarConAplicacionesEsperandoNoCierraLaTanda() {
        zone.setNutritionBatchStartTs(NOW.minusSeconds(3600).toEpochMilli());
        when(pending.countByZoneIdAndSupplyAndRuleName("MZ-2", Supply.FERTILIZER, RULE)).thenReturn(1L);

        service.apply(Change.COMPLETE, ctx(sectors(zone, 3)), RULE);

        assertThat(zone.getNutritionBatchStartTs()).isNotNull();
        assertThat(zone.getLastPlanApplicationTs()).isNull();
    }

    @Test
    void completarSinTandaNoHaceNada() {
        service.apply(Change.COMPLETE, ctx(sectors(zone, 3)), RULE);

        verify(zones, never()).save(any());
    }
}
