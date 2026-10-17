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
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload.MetricsPayload;
import com.yerbanalytics.backend.readings.ReadingCheck;
import com.yerbanalytics.backend.readings.ReadingMetrics;
import com.yerbanalytics.backend.readings.ReadingRecorder;
import com.yerbanalytics.backend.readings.ReadingValidationGuard;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulseResponse;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S-03 en la ingesta: sólo entra a la zona lo que el guard acepta, una lectura que espera su relectura no es la
 * respuesta definitiva del pulso, y las métricas descartadas llegan marcadas al contexto del motor.
 */
@DisplayName("NurseryService.updateTelemetry - lectura inválida (S-03)")
class NurseryServiceInvalidReadingTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T13:05:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);
    private static final long TS_PREVIO = AHORA.toEpochMilli() - 3_600_000L;

    private ReadingValidationGuard guard;
    private PulsoSensadoService pulsos;
    private NurseryService service;
    private ZonaEntity zona;
    private final List<RuleContext> contextos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ZonaRepository zonaRepository = mock(ZonaRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        guard = mock(ReadingValidationGuard.class);
        pulsos = mock(PulsoSensadoService.class);
        when(pulsos.recordResponse(anyString(), anyLong())).thenReturn(new PulseResponse(true, true));
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
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(observadora), parametros, new TrazaEvaluacionStore());

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        zona.setLastReadingTime(TS_PREVIO);
        zona.setHumSusRaw(55.0);
        zona.setHumSusTs(TS_PREVIO);
        zona.setTempRaw(21.0);
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setN(1);
        sector.setZona(zona);
        zona.setSectors(List.of(sector));
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(sector));

        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuracion.getEffectiveSpecs()).thenReturn(com.yerbanalytics.backend.constant.NurseryConstants.SPECS);

        service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), configuracion, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class), pulsos,
                mock(EscalamientoNodoService.class), mock(ReadingRecorder.class), guard, RELOJ, 20);
    }

    private void telemetria(MetricsPayload metricas) {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, AHORA.toEpochMilli(), metricas));
    }

    private static MetricsPayload humedadYTemperatura(double humSus, double temp) {
        return new MetricsPayload(humSus, null, temp, null, null, null, null, null, null, null);
    }

    /** El guard rechaza {@code claves}: quedan afuera de lo aceptado, como pendientes o descartadas. */
    private void guardRechaza(Set<String> pendientes, Set<String> descartadas) {
        when(guard.check(any(ZonaEntity.class), any(ReadingMetrics.class))).thenAnswer(inv -> {
            ReadingMetrics lectura = inv.getArgument(1);
            Set<String> fuera = new java.util.HashSet<>(pendientes);
            fuera.addAll(descartadas);
            return new ReadingCheck(lectura.without(fuera), pendientes, descartadas);
        });
    }

    @Test
    @DisplayName("una lectura aceptada entera se escribe en la zona, con la marca propia de la humedad")
    void lecturaAceptada() {
        when(guard.check(any(ZonaEntity.class), any(ReadingMetrics.class)))
                .thenAnswer(inv -> ReadingCheck.allAccepted(inv.getArgument(1)));

        telemetria(humedadYTemperatura(41.0, 23.0));

        assertThat(zona.getHumSusRaw()).isEqualTo(41.0);
        assertThat(zona.getHumSusTs()).isEqualTo(AHORA.toEpochMilli());
        assertThat(zona.getTempRaw()).isEqualTo(23.0);
    }

    @Test
    @DisplayName("el guard ve la lectura en unidades de la plataforma: la CE ya viene en dS/m")
    void elGuardVeLaCeConvertida() {
        when(guard.check(any(ZonaEntity.class), any(ReadingMetrics.class)))
                .thenAnswer(inv -> ReadingCheck.allAccepted(inv.getArgument(1)));

        telemetria(new MetricsPayload(null, null, null, null, null, 12_000.0, null, null, null, null));

        ArgumentCaptor<ReadingMetrics> lectura = ArgumentCaptor.forClass(ReadingMetrics.class);
        verify(guard).check(eq(zona), lectura.capture());
        assertThat(lectura.getValue().get("ce")).isEqualTo(12.0);
        assertThat(zona.getCeRaw()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("la métrica que el guard rechaza NO entra a la zona: queda el último valor bueno y su marca")
    void valorInvalidoNoEntra() {
        guardRechaza(Set.of("humSus"), Set.of());

        telemetria(humedadYTemperatura(112.0, 23.0));

        assertThat(zona.getHumSusRaw()).isEqualTo(55.0);
        assertThat(zona.getHumSusTs()).isEqualTo(TS_PREVIO);
        // Las demás métricas de la lectura sí se guardan: sólo se descarta la inválida.
        assertThat(zona.getTempRaw()).isEqualTo(23.0);
        assertThat(zona.getLastReadingTime()).isEqualTo(AHORA.toEpochMilli());
    }

    @Test
    @DisplayName("una lectura que espera su relectura no es la respuesta del pulso: no lo consume ni corre el motor")
    void esperaRelecturaNoConsumeElPulso() {
        guardRechaza(Set.of("humSus"), Set.of());

        telemetria(humedadYTemperatura(112.0, 23.0));

        verify(pulsos, never()).recordResponse(anyString(), anyLong());
        assertThat(contextos).isEmpty();
    }

    @Test
    @DisplayName("la relectura que corrige sí responde al pulso y el motor decide con el valor corregido")
    void relecturaCorrigeYCorreElMotor() {
        guardRechaza(Set.of("humSus"), Set.of());
        telemetria(humedadYTemperatura(112.0, 23.0));
        when(guard.check(any(ZonaEntity.class), any(ReadingMetrics.class)))
                .thenAnswer(inv -> ReadingCheck.allAccepted(inv.getArgument(1)));

        telemetria(humedadYTemperatura(38.0, 23.0));

        verify(pulsos).recordResponse(eq("MZ-1"), anyLong());
        assertThat(zona.getHumSusRaw()).isEqualTo(38.0);
        assertThat(contextos).hasSize(1);
        assertThat(contextos.get(0).invalidMetrics()).isEmpty();
    }

    @Test
    @DisplayName("agotadas las relecturas, el motor corre con esa métrica marcada inválida; las demás siguen vigentes")
    void descartadaLlegaAlContexto() {
        guardRechaza(Set.of(), Set.of("humSus"));

        telemetria(humedadYTemperatura(112.0, 23.0));

        verify(pulsos).recordResponse(eq("MZ-1"), anyLong());
        assertThat(contextos).hasSize(1);
        RuleContext ctx = contextos.get(0);
        assertThat(ctx.isMetricValid("humSus")).isFalse();
        assertThat(ctx.isMetricValid("temp")).isTrue();
        assertThat(ctx.validMetricRaw("humSus")).isNull();
        // El último valor bueno sigue en la zona (el dashboard lo muestra), pero las reglas no lo usan.
        assertThat(zona.getHumSusRaw()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("una lectura anterior a la última guardada se ignora sin pasar por el guard")
    void lecturaVieja() {
        zona.setLastReadingTime(AHORA.toEpochMilli() + 60_000L);

        telemetria(humedadYTemperatura(112.0, 23.0));

        verify(guard, never()).check(any(), any());
    }
}
