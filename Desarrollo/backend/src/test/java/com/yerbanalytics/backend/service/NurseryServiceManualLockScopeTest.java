package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alcance del bloqueo manual (reglas_v2 S-01): un bloqueo de macro-zona alcanza a todos sus sectores; uno de sector,
 * sólo a ese sector. El alcance lo resuelve {@code NurseryService} al armar el contexto; {@code ManualLockRule} sólo
 * lee el resultado.
 */
@DisplayName("NurseryService - alcance del bloqueo manual")
class NurseryServiceManualLockScopeTest {

    private static final Instant NOW = Instant.parse("2026-10-05T13:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZonaHorariaVivero.ZONA);

    private ActionExecutor actionExecutor;
    private ManualLockRepository locks;
    private NurseryService service;

    @BeforeEach
    void setUp() {
        ZonaRepository zones = mock(ZonaRepository.class);
        SectorRepository sectors = mock(SectorRepository.class);
        actionExecutor = mock(ActionExecutor.class);
        locks = mock(ManualLockRepository.class);
        DespachoRiego dispatch = mock(DespachoRiego.class);
        CatalogoParametrosService parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        Rule noop = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                return List.of(RuleAction.noopInfo("ReglaFalsa", "nada"));
            }
        };
        TrazaEvaluacionStore store = new TrazaEvaluacionStore();
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(noop), parameters, store);

        ZonaEntity zone = RuleContextTestFactory.zonaBasica("MZ-1");
        zone.setLastReadingTime(null);
        List<SectorEntity> zoneSectors = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            SectorEntity s = RuleContextTestFactory.sectorBasico(String.format("MZ-1-%03d", n));
            s.setN(n);
            s.setZona(zone);
            zoneSectors.add(s);
        }
        zone.setSectors(zoneSectors);
        when(zones.findById("MZ-1")).thenReturn(Optional.of(zone));
        when(zones.findAllWithSectors()).thenReturn(List.of(zone));
        when(sectors.findByZonaId("MZ-1")).thenReturn(zoneSectors);

        ConfiguracionService configuration = mock(ConfiguracionService.class);
        when(configuration.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());

        service = new NurseryService(new NurseryProperties(), zones, sectors,
                mock(HistorialService.class), configuration, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor, mock(WeatherService.class),
                locks, mock(DiagnosticoService.class), store,
                mock(HistorialRepository.class), dispatch, PulsosDePrueba.conMotor(),
                mock(EscalamientoNodoService.class), mock(com.yerbanalytics.backend.readings.ReadingRecorder.class),
                CLOCK, 20);
    }

    private static ManualLockEntity zoneLock() {
        ManualLockEntity e = new ManualLockEntity();
        e.setZonaId("MZ-1");
        e.setActive(true);
        return e;
    }

    private static ManualLockEntity sectorLock(String sectorId) {
        ManualLockEntity e = new ManualLockEntity();
        e.setSectorId(sectorId);
        e.setActive(true);
        return e;
    }

    /** Qué sectores de MZ-1 llegaron bloqueados al motor tras una lectura. */
    private Map<String, Boolean> lockedBySector() {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, NOW.getEpochSecond() - 5,
                new MqttTelemetryPayload.MetricsPayload(40.0, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null)));
        ArgumentCaptor<RuleContext> ctx = ArgumentCaptor.forClass(RuleContext.class);
        verify(actionExecutor, times(3)).execute(any(), ctx.capture(), eq(OrigenEvaluacion.TELEMETRIA));
        return ctx.getAllValues().stream().collect(Collectors.toMap(
                c -> c.sector().getId(), RuleContext::bloqueoManualActivo, (a, b) -> a));
    }

    @Test
    @DisplayName("sin bloqueos ningún sector llega bloqueado")
    void noLocksNothingLocked() {
        assertThat(lockedBySector().values()).containsOnly(false);
    }

    @Test
    @DisplayName("un bloqueo de macro-zona alcanza a todos los sectores de la zona")
    void zoneLockReachesEverySector() {
        when(locks.findByZonaIdAndActiveTrue("MZ-1")).thenReturn(List.of(zoneLock()));

        assertThat(lockedBySector()).containsOnlyKeys("MZ-1-001", "MZ-1-002", "MZ-1-003")
                .containsValues(true).doesNotContainValue(false);
    }

    @Test
    @DisplayName("un bloqueo de sector alcanza sólo a ese sector")
    void sectorLockReachesOnlyThatSector() {
        when(locks.findBySectorIdAndActiveTrue("MZ-1-002")).thenReturn(List.of(sectorLock("MZ-1-002")));

        Map<String, Boolean> locked = lockedBySector();

        assertThat(locked).containsEntry("MZ-1-001", false).containsEntry("MZ-1-002", true)
                .containsEntry("MZ-1-003", false);
    }
}
