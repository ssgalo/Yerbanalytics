package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
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
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S-05 en la ingesta: las fallas de hardware se consultan UNA vez por zona evaluada y cada sector recibe las suyas. */
@DisplayName("NurseryService.updateTelemetry - fallas de hardware (S-05)")
class NurseryServiceFaultsTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T13:05:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private ZonaRepository zonaRepository;
    private SectorRepository sectorRepository;
    private ConfiguracionService configuracion;
    private RuleOrchestrator orchestrator;
    private HardwareService hardwareService;
    private final List<RuleContext> contextos = new ArrayList<>();
    private NurseryService service;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        sectorRepository = mock(SectorRepository.class);
        hardwareService = mock(HardwareService.class);
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        Rule observadora = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "Observadora"; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                contextos.add(ctx);
                return List.of(RuleAction.noopInfo("Observadora", "nada"));
            }
        };
        orchestrator = new RuleOrchestrator(List.of(observadora), parametros, new TrazaEvaluacionStore());

        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        zona.setLastReadingTime(null);
        SectorEntity completo = sector("MZ-1-001", zona, 1);
        SectorEntity sinNada = sector("MZ-1-002", zona, 2);
        zona.setSectors(List.of(completo, sinNada));
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(completo, sinNada));
        when(hardwareService.coverageOf(anyString(), anyCollection()))
                .thenReturn(Map.of("MZ-1-001", HardwareCoverage.COMPLETE, "MZ-1-002", HardwareCoverage.COMPLETE));
        // Sólo el primer sector tiene la electroválvula averiada; el segundo no figura (nada marcado).
        when(hardwareService.faultsOf(anyCollection()))
                .thenReturn(Map.of("MZ-1-001", new HardwareFaults("SIN_CAUDAL")));

        configuracion = mock(ConfiguracionService.class);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuracion.getEffectiveSpecs()).thenReturn(NurseryConstants.SPECS);

        service = servicioCon(PulsosDePrueba.conMotor());
    }

    private NurseryService servicioCon(PulsoSensadoService pulsos) {
        return new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), configuracion, hardwareService,
                mock(TopologiaLayoutRepository.class), orchestrator, mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class),
                pulsos, mock(EscalamientoNodoService.class), mock(ReadingRecorder.class),
                ReadingGuardsDePrueba.aceptaTodo(), RELOJ, 20);
    }

    private static SectorEntity sector(String id, ZonaEntity zona, int n) {
        SectorEntity s = RuleContextTestFactory.sectorBasico(id);
        s.setN(n);
        s.setZona(zona);
        return s;
    }

    private void telemetria() {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(41.0, 70.0, 23.0, 22.0, 40.0, null, null, null, null, null)));
    }

    @Test
    @DisplayName("cada sector evalúa con las fallas que tiene marcadas; el que no figura se lee sin ninguna")
    void cadaSectorSusFallas() {
        telemetria();

        assertThat(contextos).hasSize(2);
        assertThat(contextos.get(0).sector().getId()).isEqualTo("MZ-1-001");
        assertThat(contextos.get(0).faults()).isEqualTo(new HardwareFaults("SIN_CAUDAL"));
        assertThat(contextos.get(1).sector().getId()).isEqualTo("MZ-1-002");
        assertThat(contextos.get(1).faults()).isEqualTo(HardwareFaults.NONE);
    }

    @Test
    @DisplayName("consulta las fallas una sola vez por evaluación de la zona, no una por sector")
    void unaConsultaPorZona() {
        telemetria();

        verify(hardwareService, times(1)).faultsOf(anyCollection());
    }

    @Test
    @DisplayName("sin motor (lectura fuera de un pulso con motor) no se consultan las fallas")
    void sinMotorNoConsulta() {
        service = servicioCon(PulsosDePrueba.sinMotor());

        telemetria();

        assertThat(contextos).isEmpty();
        verify(hardwareService, never()).faultsOf(any());
    }
}
