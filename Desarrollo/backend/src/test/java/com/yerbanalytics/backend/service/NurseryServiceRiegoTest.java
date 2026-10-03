package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RiegoCtx;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.CicloLectura;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tareas 10.2 y 10.3: el contexto de riego se arma con UNA consulta por zona y el inicio de ciclo del
 * {@code Clock}; el snapshot toma la válvula del despacho.
 */
@DisplayName("NurseryService - contexto de riego y estado de la válvula")
class NurseryServiceRiegoTest {

    /** 10:05 locales del 5/10/2026. */
    private static final Instant AHORA = Instant.parse("2026-10-05T13:05:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private ActionExecutor actionExecutor;
    private HistorialRepository historialRepository;
    private DespachoRiego despacho;
    private WeatherService weatherService;
    private ZonaRepository zonaRepository;
    private NurseryService service;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        actionExecutor = mock(ActionExecutor.class);
        historialRepository = mock(HistorialRepository.class);
        weatherService = mock(WeatherService.class);
        despacho = mock(DespachoRiego.class);
        when(despacho.finRiegoEnCurso(any())).thenReturn(null);   // Mockito devolvería 0L para un Long
        when(despacho.ultimoRiegoMs(any())).thenReturn(null);
        when(despacho.ultimoRiegoCriticoMs(any())).thenReturn(null);
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        Rule regla = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                return List.of(RuleAction.noopInfo("ReglaFalsa", "nada"));
            }
        };
        TrazaEvaluacionStore store = new TrazaEvaluacionStore();
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(regla), parametros, store);

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        List<SectorEntity> sectores = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            SectorEntity s = RuleContextTestFactory.sectorBasico(String.format("MZ-1-%03d", n));
            s.setN(n);
            s.setZona(zona);
            sectores.add(s);
        }
        zona.setSectors(sectores);
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(zonaRepository.findAllWithSectors()).thenReturn(List.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(sectores);

        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());

        service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), configuracion, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor, weatherService,
                mock(ManualLockRepository.class), mock(DiagnosticoService.class), store, parametros,
                historialRepository, despacho, RELOJ, 20);
    }

    private static HistorialRepository.UltimoEvento fila(String sector, String tipo, String regla, long ts) {
        return new HistorialRepository.UltimoEvento() {
            @Override public String getSectorId() { return sector; }
            @Override public String getTipo() { return tipo; }
            @Override public String getRegla() { return regla; }
            @Override public Long getTs() { return ts; }
        };
    }

    private void telemetria() {
        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, AHORA.getEpochSecond() - 5,
                new MqttTelemetryPayload.MetricsPayload(40.0, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null)));
    }

    private List<RuleContext> contextosEvaluados() {
        ArgumentCaptor<RuleContext> ctx = ArgumentCaptor.forClass(RuleContext.class);
        verify(actionExecutor, times(3)).execute(any(), ctx.capture(), eq(OrigenEvaluacion.TELEMETRIA));
        return ctx.getAllValues();
    }

    @Test
    @DisplayName("el pronóstico se pide UNA vez por evaluación de zona y sin espera, no una vez por sector")
    void unPronosticoPorZonaYSinEspera() {
        WeatherForecast pronostico = RiegoCtx.pronostico(AHORA, new double[]{90, 90}, new double[]{3, 3});
        when(weatherService.getForecastSinEspera()).thenReturn(pronostico);

        telemetria();

        verify(weatherService, times(1)).getForecastSinEspera();
        verify(weatherService, never()).getForecast();
        assertThat(contextosEvaluados()).hasSize(3).allSatisfy(ctx -> assertThat(ctx.forecast()).isSameAs(pronostico));
    }

    @Test
    @DisplayName("una sola consulta de historial por zona, aunque la zona tenga varios sectores")
    void unaConsultaPorZona() {
        telemetria();

        ArgumentCaptor<Long> desde = ArgumentCaptor.forClass(Long.class);
        verify(historialRepository, times(1)).ultimosPorSector(eq("MZ-1"), desde.capture());
        // Mira lo bastante atrás para la pausa y el tope más largos del catálogo (24 h).
        assertThat(desde.getValue()).isLessThanOrEqualTo(AHORA.toEpochMilli() - 24L * 3_600_000L);
    }

    @Test
    @DisplayName("el inicio de ciclo sale del Clock y del intervalo de sensado (240 min → 10:00)")
    void inicioDeCicloDelClock() {
        telemetria();

        assertThat(contextosEvaluados()).allSatisfy(ctx ->
                assertThat(ctx.riego().inicioCiclo()).isEqualTo(CicloLectura.inicio(AHORA, 240))
                        .isEqualTo(Instant.parse("2026-10-05T13:00:00Z")));
    }

    @Test
    @DisplayName("el último riego que recuerda el despacho en memoria gana si es más reciente que el del historial")
    void ultimoRiegoEnMemoriaGanaSiEsMasReciente() {
        long hace3h = AHORA.toEpochMilli() - 3 * 3_600_000L;
        long hace10min = AHORA.toEpochMilli() - 600_000L;
        when(historialRepository.ultimosPorSector(eq("MZ-1"), anyLong())).thenReturn(List.of(
                fila("MZ-1-001", "Riego", "RiegoPorDeficitRule", hace3h)));
        when(despacho.ultimoRiegoMs("MZ-1-001")).thenReturn(hace10min);          // el registro del último falló
        when(despacho.ultimoRiegoMs("MZ-1-002")).thenReturn(hace10min);          // el historial ni lo vio
        when(despacho.ultimoRiegoCriticoMs("MZ-1-002")).thenReturn(hace10min);

        telemetria();

        var ctxs = contextosEvaluados();
        var s1 = ctxs.stream().filter(c -> c.sector().getId().equals("MZ-1-001")).findFirst().orElseThrow().riego();
        assertThat(s1.ultimoRiegoMs()).isEqualTo(hace10min);
        assertThat(s1.ultimoRiegoCriticoMs()).isNull();
        var s2 = ctxs.stream().filter(c -> c.sector().getId().equals("MZ-1-002")).findFirst().orElseThrow().riego();
        assertThat(s2.ultimoRiegoMs()).isEqualTo(hace10min);
        assertThat(s2.ultimoRiegoCriticoMs()).isEqualTo(hace10min);
    }

    @Test
    @DisplayName("cada sector recibe su último riego, el último crítico, su última aplicación y su riego en curso")
    void contextoPorSector() {
        long hace3h = AHORA.toEpochMilli() - 3 * 3_600_000L;
        long hace1h = AHORA.toEpochMilli() - 3_600_000L;
        long hace7h = AHORA.toEpochMilli() - 7 * 3_600_000L;
        long fin = AHORA.toEpochMilli() + 300_000L;
        when(historialRepository.ultimosPorSector(eq("MZ-1"), anyLong())).thenReturn(List.of(
                fila("MZ-1-001", "Riego", "RiegoPorDeficitRule", hace3h),
                fila("MZ-1-001", "Riego", "DeficitCriticoRule", hace7h),
                fila("MZ-1-001", "Insumo", null, hace1h),
                fila("MZ-1-002", "Riego", "RiegoPorDeficitRule", hace1h)));
        when(despacho.finRiegoEnCurso("MZ-1-002")).thenReturn(fin);

        telemetria();

        List<RuleContext> ctxs = contextosEvaluados();
        var s1 = ctxs.stream().filter(c -> c.sector().getId().equals("MZ-1-001")).findFirst().orElseThrow().riego();
        assertThat(s1.ultimoRiegoMs()).isEqualTo(hace3h);              // el mayor ts entre sus filas "Riego"
        assertThat(s1.ultimoRiegoCriticoMs()).isEqualTo(hace7h);       // sólo la fila de DeficitCriticoRule
        assertThat(s1.ultimaAplicacionMs()).isEqualTo(hace1h);
        assertThat(s1.riegoEnCursoHastaMs()).isNull();
        var s2 = ctxs.stream().filter(c -> c.sector().getId().equals("MZ-1-002")).findFirst().orElseThrow().riego();
        assertThat(s2.ultimoRiegoMs()).isEqualTo(hace1h);
        assertThat(s2.ultimoRiegoCriticoMs()).isNull();
        assertThat(s2.ultimaAplicacionMs()).isNull();
        assertThat(s2.riegoEnCursoHastaMs()).isEqualTo(fin);
        var s3 = ctxs.stream().filter(c -> c.sector().getId().equals("MZ-1-003")).findFirst().orElseThrow().riego();
        assertThat(s3.ultimoRiegoMs()).isNull();
        assertThat(s3.inicioCiclo()).isNotNull();
    }

    @Test
    @DisplayName("el contexto lleva la marca de la humedad de sustrato de la zona")
    void humSusTs() {
        telemetria();

        assertThat(contextosEvaluados()).allSatisfy(ctx ->
                assertThat(ctx.riego().humSusTs()).isEqualTo(zona.getHumSusTs()).isNotNull());
    }

    // ------------------------------------------------------------------ 10.3

    @Test
    @DisplayName("el snapshot toma el estado de la válvula de DespachoRiego (Regando / En cola / Cerrada)")
    void snapshotUsaElEstadoDelDespacho() {
        when(despacho.estadoValvula("MZ-1-001")).thenReturn("Regando");
        when(despacho.estadoValvula("MZ-1-002")).thenReturn("En cola");
        when(despacho.estadoValvula("MZ-1-003")).thenReturn("Cerrada");
        zona.setLastReadingTime(AHORA.toEpochMilli() - 1_000L);
        // La columna legada no manda: aunque diga otra cosa, gana el despacho.
        zona.getSectors().forEach(s -> s.setActuadorValve("Regando"));

        NurseryData data = service.getSnapshot();

        var sectores = data.zonas().get(0).sectors();
        assertThat(sectores).extracting(s -> s.actuadores().valve()).containsExactly("Regando", "En cola", "Cerrada");
        verify(despacho, atLeastOnce()).estadoValvula("MZ-1-002");
    }

    @Test
    @DisplayName("una zona fuera de servicio sigue mostrando la válvula 'Cerrada'")
    void zonaOfflineMuestraCerrada() {
        zona.setLastReadingTime(null);

        NurseryData data = service.getSnapshot();

        assertThat(data.zonas().get(0).sectors()).allSatisfy(s -> assertThat(s.actuadores().valve()).isEqualTo("Cerrada"));
    }
}
