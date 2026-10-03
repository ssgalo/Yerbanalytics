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
import com.yerbanalytics.backend.engine.traza.Evaluacion;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tarea 1.2: el motor toma la hora del {@link Clock} del vivero, no de {@code Instant.now()}.
 * Con un reloj fijo, el contexto que ven las reglas es determinístico.
 */
@DisplayName("Reloj inyectado en los llamadores del motor")
class RelojInyectadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-03T13:30:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private ActionExecutor actionExecutor;
    private ZonaRepository zonaRepository;
    private SectorRepository sectorRepository;
    private RuleOrchestrator orchestrator;
    private CatalogoParametrosService parametros;
    private TrazaEvaluacionStore store;
    private ZonaEntity zona;
    private SectorEntity s1;

    @BeforeEach
    void setUp() {
        actionExecutor = mock(ActionExecutor.class);
        zonaRepository = mock(ZonaRepository.class);
        sectorRepository = mock(SectorRepository.class);
        parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        store = new TrazaEvaluacionStore();
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
        s1.setZona(zona);
        zona.setSectors(List.of(s1));
    }

    @Test
    @DisplayName("updateTelemetry arma el RuleContext con el instante del Clock inyectado")
    void updateTelemetryUsaElClock() {
        zona.setLastReadingTime(null);
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(s1));
        NurseryService service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), mock(ConfiguracionService.class), mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor, mock(WeatherService.class),
                mock(ManualLockRepository.class), mock(DiagnosticoService.class), store, parametros, mock(com.yerbanalytics.backend.repository.HistorialRepository.class), mock(com.yerbanalytics.backend.engine.riego.DespachoRiego.class), RELOJ, 20);

        // timestamp del nodo = 10 segundos antes del "ahora" del reloj fijo, en segundos epoch.
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, AHORA.getEpochSecond() - 10,
                new MqttTelemetryPayload.MetricsPayload(38.0, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null)));

        ArgumentCaptor<RuleContext> ctx = ArgumentCaptor.forClass(RuleContext.class);
        verify(actionExecutor).execute(any(), ctx.capture(), eq(OrigenEvaluacion.TELEMETRIA));
        assertThat(ctx.getValue().now()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("evaluarTodos (barrido) arma el RuleContext con el instante del Clock inyectado")
    void barridoUsaElClock() {
        when(zonaRepository.findAllWithSectors()).thenReturn(List.of(zona));
        NurseryWatchdog watchdog = new NurseryWatchdog(zonaRepository, sectorRepository, orchestrator,
                actionExecutor, mock(ConfiguracionService.class), mock(WeatherService.class),
                mock(ManualLockRepository.class), store, RELOJ);

        watchdog.evaluarTodos();

        ArgumentCaptor<RuleContext> ctx = ArgumentCaptor.forClass(RuleContext.class);
        verify(actionExecutor).execute(any(), ctx.capture(), eq(OrigenEvaluacion.BARRIDO));
        assertThat(ctx.getValue().now()).isEqualTo(AHORA);
    }
}
