package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tarea 3.6: los dos llamadores del motor guardan la traza con su origen y siguen ejecutando
 * exactamente las acciones del orquestador, sin llamadas extra al historial.
 */
@DisplayName("Llamadores del motor - traza")
class TrazaEvaluacionLlamadoresTest {

    private TrazaEvaluacionStore store;
    private CatalogoParametrosService parametros;
    private ActionExecutor actionExecutor;
    private HistorialService historialService;
    private ConfiguracionService configuracionService;
    private ZonaRepository zonaRepository;
    private SectorRepository sectorRepository;
    private WeatherService weatherService;
    private ManualLockRepository manualLockRepository;
    private RuleOrchestrator orchestrator;
    private ZonaEntity zona;
    private SectorEntity s1;
    private SectorEntity s2;

    @BeforeEach
    void setUp() {
        store = new TrazaEvaluacionStore();
        actionExecutor = mock(ActionExecutor.class);
        historialService = mock(HistorialService.class);
        configuracionService = mock(ConfiguracionService.class);
        zonaRepository = mock(ZonaRepository.class);
        sectorRepository = mock(SectorRepository.class);
        weatherService = mock(WeatherService.class);
        manualLockRepository = mock(ManualLockRepository.class);

        parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(com.yerbanalytics.backend.engine.ReglaTestSupport.fabrica());
        Rule regla = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                return List.of(RuleAction.noopInfo("ReglaFalsa", "nada que hacer"));
            }
        };
        orchestrator = new RuleOrchestrator(List.of(regla), parametros, store);

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        s1 = RuleContextTestFactory.sectorBasico("MZ-1-001");
        s2 = RuleContextTestFactory.sectorBasico("MZ-1-002");
        s1.setZona(zona);
        s2.setZona(zona);
        zona.setSectors(List.of(s1, s2));
    }

    @Test
    void updateTelemetry_guardaUnaTrazaTelemetriaPorSectorYEjecutaLasAccionesDeSiempre() {
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(s1, s2));
        NurseryService service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                historialService, configuracionService, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor, weatherService,
                manualLockRepository, mock(DiagnosticoService.class), store, parametros, mock(com.yerbanalytics.backend.repository.HistorialRepository.class), mock(com.yerbanalytics.backend.engine.riego.DespachoRiego.class),
                java.time.Clock.system(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA), 20);

        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, System.currentTimeMillis(),
                new MqttTelemetryPayload.MetricsPayload(38.0, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null)));

        for (String sector : List.of("MZ-1-001", "MZ-1-002")) {
            assertThat(store.ultima(sector, OrigenEvaluacion.TELEMETRIA)).isPresent();
            assertThat(store.ultima(sector, OrigenEvaluacion.BARRIDO)).isEmpty();
        }
        ArgumentCaptor<List<RuleAction>> acciones = ArgumentCaptor.forClass(List.class);
        verify(actionExecutor, times(2)).execute(acciones.capture(), any(RuleContext.class), eq(OrigenEvaluacion.TELEMETRIA));
        assertThat(acciones.getAllValues()).allSatisfy(l -> assertThat(l).extracting(RuleAction::type)
                .containsExactly(ActionType.NOOP_INFO));
    }

    @Test
    void elStoreYElOrquestadorNoDependenDelHistorialNiDeRepositorios() {
        // Guardar la traza no puede escribir en la base: ni el store ni el orquestador reciben
        // un HistorialService ni un repositorio, así que no tienen cómo hacerlo.
        for (Class<?> c : List.of(TrazaEvaluacionStore.class, RuleOrchestrator.class)) {
            List<Class<?>> tipos = new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                tipos.add(f.getType());
            }
            for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
                tipos.addAll(List.of(k.getParameterTypes()));
            }
            assertThat(tipos).as(c.getSimpleName())
                    .doesNotContain(HistorialService.class)
                    .noneMatch(org.springframework.data.repository.Repository.class::isAssignableFrom);
        }
    }

    @Test
    void evaluarTodos_guardaUnaTrazaBarridoPorSectorYNoPisaLaDeTelemetria() {
        // Una traza de telemetría previa, que el barrido no debe tapar.
        store.guardar(orchestrator.evaluate(
                RuleContextTestFactory.basico(s1, zona), OrigenEvaluacion.TELEMETRIA).traza());
        when(zonaRepository.findAllWithSectors()).thenReturn(List.of(zona));
        NurseryWatchdog watchdog = new NurseryWatchdog(zonaRepository, sectorRepository, orchestrator,
                actionExecutor, configuracionService, weatherService, manualLockRepository, store,
                java.time.Clock.system(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA));

        watchdog.evaluarTodos();

        for (String sector : List.of("MZ-1-001", "MZ-1-002")) {
            assertThat(store.ultima(sector, OrigenEvaluacion.BARRIDO)).isPresent();
        }
        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA)).isPresent();
        assertThat(store.ultima("MZ-1-002", OrigenEvaluacion.TELEMETRIA)).isEmpty();
        verify(actionExecutor, times(2)).execute(any(), any(RuleContext.class), eq(OrigenEvaluacion.BARRIDO));
    }
}
