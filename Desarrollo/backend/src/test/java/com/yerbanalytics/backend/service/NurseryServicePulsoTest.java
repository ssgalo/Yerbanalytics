package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.NurseryData;
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
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La ingesta frente al pulso: el motor corre sólo con la respuesta a un pulso con motor, la lectura no toca el
 * diagnóstico, el diagnóstico se marca usado cuando lo usó una regla, y el snapshot pone la zona fuera de servicio
 * por la cuenta de pulsos sin respuesta (no por la antigüedad de la lectura).
 */
@DisplayName("NurseryService - pulso de sensado")
class NurseryServicePulsoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T13:05:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private ActionExecutor actionExecutor;
    private PulsoSensadoService pulsos;
    private EscalamientoNodoService escalamiento;
    private ZonaRepository zonaRepository;
    private SectorRepository sectorRepository;
    private NurseryService service;
    private ZonaEntity zona;
    private SectorEntity sector;
    private final List<String> evaluadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        sectorRepository = mock(SectorRepository.class);
        actionExecutor = mock(ActionExecutor.class);
        pulsos = mock(PulsoSensadoService.class);
        escalamiento = mock(EscalamientoNodoService.class);
        DespachoRiego despacho = mock(DespachoRiego.class);
        when(despacho.finRiegoEnCurso(any())).thenReturn(null);
        when(despacho.ultimoRiegoMs(any())).thenReturn(null);
        when(despacho.ultimoRiegoCriticoMs(any())).thenReturn(null);
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        Rule dosis = new Rule() {
            @Override public int priority() { return 11; }
            @Override public String name() { return "DosisFalsa"; }
            @Override public RuleBranch branch() { return RuleBranch.INSUMO; }
            @Override public boolean usaDiagnostico() { return true; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                evaluadas.add(ctx.sector().getId());
                return List.of(RuleAction.noopInfo("DosisFalsa", "nada"));
            }
        };
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(dosis), parametros, new TrazaEvaluacionStore());

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        zona.setLastReadingTime(null);   // la fábrica la pone en la hora real: con el reloj fijo, la lectura sería "vieja"
        sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setN(1);
        sector.setZona(zona);
        sector.setDiagnosisEstado("Clorosis");
        sector.setDiagnosisConf(91.0);
        sector.setDiagnosisSev("Media");
        zona.setSectors(List.of(sector));
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(zonaRepository.findAllWithSectors()).thenReturn(List.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(sector));

        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuracion.getEffectiveSpecs()).thenReturn(NurseryConstants.SPECS);

        service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), configuracion, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor, mock(WeatherService.class),
                mock(ManualLockRepository.class), mock(DiagnosticoService.class), new TrazaEvaluacionStore(),
                mock(HistorialRepository.class), despacho, pulsos, escalamiento, RELOJ, 20);
    }

    private void telemetria() {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(60.0, 70.0, 24.0, 22.0, 40.0, null, null, null, null, null)));
    }

    @Test
    @DisplayName("fuera de un pulso con motor: persiste la lectura y el estado de los sectores, sin motor")
    void sinPulsoConMotor_noEvalua() {
        when(pulsos.respondePulso(anyString(), anyLong())).thenReturn(false);
        sector.setStatus("critical");

        telemetria();

        assertThat(zona.getLastReadingTime()).isEqualTo(AHORA.toEpochMilli());
        assertThat(sector.getStatus()).isNotEqualTo("critical");
        verify(zonaRepository).save(zona);
        verify(sectorRepository).saveAll(List.of(sector));
        verify(actionExecutor, never()).execute(any(), any(), any());
        assertThat(evaluadas).isEmpty();
    }

    @Test
    @DisplayName("la lectura no toca el diagnóstico del sector")
    void laLecturaNoPisaElDiagnostico() {
        when(pulsos.respondePulso(anyString(), anyLong())).thenReturn(true);

        telemetria();

        assertThat(sector.getDiagnosisEstado()).isEqualTo("Clorosis");
        assertThat(sector.getDiagnosisConf()).isEqualTo(91.0);
        assertThat(sector.getDiagnosisSev()).isEqualTo("Media");
    }

    @Test
    @DisplayName("primer pulso con un diagnóstico nuevo lo usa y lo marca; el siguiente lo omite")
    void diagnosticoNuevoSeUsaUnaVez() {
        when(pulsos.respondePulso(anyString(), anyLong())).thenReturn(true);
        sector.setDiagnosisTs(AHORA.toEpochMilli() - 3_600_000L);

        telemetria();
        assertThat(evaluadas).containsExactly("MZ-1-001");
        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(sector.getDiagnosisTs());

        telemetria();
        assertThat(evaluadas).as("el diagnóstico ya se usó").hasSize(1);
    }

    @Test
    @DisplayName("sin diagnóstico nuevo, la regla de diagnóstico no corre y no se marca nada")
    void sinDiagnosticoNuevo_noMarca() {
        when(pulsos.respondePulso(anyString(), anyLong())).thenReturn(true);

        telemetria();

        assertThat(evaluadas).isEmpty();
        assertThat(sector.getDiagnosisUsadoTs()).isNull();
    }

    @Test
    @DisplayName("toda telemetría corta la racha de pulsos sin respuesta")
    void todaTelemetriaReiniciaLaCuenta() {
        telemetria();
        telemetria();

        verify(escalamiento, times(2)).reiniciar(zona);
    }

    @Test
    @DisplayName("snapshot: la zona con 3 pulsos sin respuesta está fuera de servicio")
    void snapshot_fueraDeServicioPorLaCuenta() {
        zona.setLastReadingTime(AHORA.toEpochMilli() - 1_000L);
        zona.setPulsosSinRespuesta(3);

        NurseryData data = service.getSnapshot();

        assertThat(data.zonas().get(0).lectura().stale()).isTrue();
        assertThat(data.zonas().get(0).sectors()).allSatisfy(s -> assertThat(s.status()).isEqualTo("offline"));
    }

    @Test
    @DisplayName("snapshot: una lectura vieja (entre pulsos) no pone la zona fuera de servicio")
    void snapshot_lecturaViejaNoEsFueraDeServicio() {
        zona.setLastReadingTime(AHORA.toEpochMilli() - 3 * 3_600_000L);
        zona.setPulsosSinRespuesta(2);

        NurseryData data = service.getSnapshot();

        assertThat(data.zonas().get(0).lectura().stale()).isFalse();
        assertThat(data.zonas().get(0).sectors()).noneSatisfy(s -> assertThat(s.status()).isEqualTo("offline"));
    }
}
