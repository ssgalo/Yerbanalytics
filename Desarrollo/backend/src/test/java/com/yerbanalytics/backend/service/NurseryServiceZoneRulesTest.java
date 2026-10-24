package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.dto.NutritionPlan;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.readings.ReadingRecorder;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las reglas de macro-zona (plan de nutrición) en la ingesta: una vez por zona y sólo con el motor encendido. */
@DisplayName("NurseryService - reglas de macro-zona")
class NurseryServiceZoneRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-17T13:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZonaHorariaVivero.ZONA);

    private ZonaRepository zoneRepository;
    private SectorRepository sectorRepository;
    private HardwareService hardwareService;
    private ZoneRuleEngine zoneRules;
    private NutritionPlanService nutritionPlan;
    private ZonaEntity zone;
    private List<SectorEntity> sectors;
    private NurseryService service;

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ZonaRepository.class);
        sectorRepository = mock(SectorRepository.class);
        hardwareService = mock(HardwareService.class);
        zoneRules = mock(ZoneRuleEngine.class);
        nutritionPlan = mock(NutritionPlanService.class);

        zone = RuleContextTestFactory.zonaBasica("MZ-1");
        zone.setLastReadingTime(null);
        SectorEntity a = sector("MZ-1-001", 1);
        SectorEntity b = sector("MZ-1-002", 2);
        sectors = List.of(a, b);
        zone.setSectors(sectors);
        when(zoneRepository.findById("MZ-1")).thenReturn(Optional.of(zone));
        when(zoneRepository.findAllWithSectors()).thenReturn(List.of(zone));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(sectors);
        when(hardwareService.coverageOf(anyString(), anyCollection()))
                .thenReturn(Map.of("MZ-1-001", HardwareCoverage.COMPLETE));
        when(hardwareService.faultsOf(anyCollection())).thenReturn(Map.of("MZ-1-001", HardwareFaults.NONE));

        service = serviceWith(PulsosDePrueba.conMotor());
        service.setZoneRules(zoneRules);
        service.setNutritionPlan(nutritionPlan);
    }

    private NurseryService serviceWith(PulsoSensadoService pulses) {
        CatalogoParametrosService parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        ConfiguracionService configuration = mock(ConfiguracionService.class);
        when(configuration.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuration.getEffectiveSpecs()).thenReturn(NurseryConstants.SPECS);
        return new NurseryService(new NurseryProperties(), zoneRepository, sectorRepository,
                mock(HistorialService.class), configuration, hardwareService, mock(TopologiaLayoutRepository.class),
                new RuleOrchestrator(List.of(), parameters, new TrazaEvaluacionStore()), mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class),
                pulses, mock(EscalamientoNodoService.class), mock(ReadingRecorder.class),
                ReadingGuardsDePrueba.aceptaTodo(), CLOCK, 20);
    }

    private SectorEntity sector(String id, int n) {
        SectorEntity s = RuleContextTestFactory.sectorBasico(id);
        s.setN(n);
        s.setZona(zone);
        return s;
    }

    private void telemetry() {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(41.0, 70.0, 23.0, 22.0, 40.0, null, null, null, null, null)));
    }

    @Test
    @DisplayName("con el motor encendido evalúa las reglas de la zona UNA vez, con sus sectores y lo que armó por sector")
    void evaluatesTheZoneOnce() {
        telemetry();

        verify(zoneRules).evaluate(eq(zone), eq(sectors), eq(NOW), eq(Map.of("MZ-1-001", HardwareCoverage.COMPLETE)),
                eq(Map.of("MZ-1-001", HardwareFaults.NONE)), any(), any());
    }

    @Test
    @DisplayName("sin motor (una lectura que no responde a un pulso con motor) no se evalúa la zona")
    void noEngineNoZoneRules() {
        service = serviceWith(PulsosDePrueba.sinMotor());
        service.setZoneRules(zoneRules);

        telemetry();

        verify(zoneRules, never()).evaluate(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sin el motor de zonas conectado la ingesta evalúa sólo los sectores y no falla")
    void withoutZoneRulesEngine() {
        service = serviceWith(PulsosDePrueba.conMotor());

        telemetry();

        verify(zoneRules, never()).evaluate(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("el snapshot trae el plan de nutrición de cada zona")
    void snapshotCarriesTheNutritionPlan() {
        NutritionPlan plan = new NutritionPlan(14, true, 1_000L, 2_000L, false, false);
        when(nutritionPlan.status(zone)).thenReturn(plan);

        NurseryData data = service.getSnapshot();

        assertThat(data.zonas().get(0).nutritionPlan()).isEqualTo(plan);
    }

    @Test
    @DisplayName("sin el servicio del plan el snapshot no lo trae")
    void snapshotWithoutService() {
        service = serviceWith(PulsosDePrueba.conMotor());

        assertThat(service.getSnapshot().zonas().get(0).nutritionPlan()).isNull();
    }
}
