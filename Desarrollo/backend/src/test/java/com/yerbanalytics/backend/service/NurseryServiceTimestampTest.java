package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El {@code timestamp} del nodo (segundos epoch del firmware, milisegundos del simulador) se
 * normaliza a ms en la ingesta: lo guardado en la zona y lo que recibe el heartbeat es el mismo
 * valor, y una lectura vieja del buffer offline no pisa una más nueva.
 */
@DisplayName("NurseryService.updateTelemetry - timestamp del nodo")
class NurseryServiceTimestampTest {

    private ZonaRepository zonaRepository;
    private HardwareService hardwareService;
    private ActionExecutor actionExecutor;
    private NurseryService service;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        hardwareService = mock(HardwareService.class);
        actionExecutor = mock(ActionExecutor.class);
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        Rule regla = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                return List.of(RuleAction.noopInfo("ReglaFalsa", "nada que hacer"));
            }
        };
        TrazaEvaluacionStore store = new TrazaEvaluacionStore();
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(regla), parametros, store);

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        SectorEntity s1 = RuleContextTestFactory.sectorBasico("MZ-1-001");
        s1.setZona(zona);
        zona.setSectors(List.of(s1));
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(s1));

        service = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), mock(ConfiguracionService.class), hardwareService,
                mock(TopologiaLayoutRepository.class), orchestrator, actionExecutor,
                mock(WeatherService.class), mock(ManualLockRepository.class),
                mock(DiagnosticoService.class), store, parametros, mock(com.yerbanalytics.backend.repository.HistorialRepository.class), mock(com.yerbanalytics.backend.engine.riego.DespachoRiego.class),
                java.time.Clock.system(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA), 20);
    }

    private static MqttTelemetryPayload payload(Long timestamp) {
        return new MqttTelemetryPayload("aa:bb", 90, -60, timestamp,
                new MqttTelemetryPayload.MetricsPayload(38.0, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null));
    }

    @Test
    @DisplayName("segundos epoch del firmware: se guardan en ms y el heartbeat recibe el mismo valor")
    void segundosEpochSeGuardanEnMs() {
        long ahoraS = System.currentTimeMillis() / 1000;
        zona.setLastReadingTime(null);

        service.updateTelemetry("MZ-1", payload(ahoraS));

        assertThat(zona.getLastReadingTime()).isEqualTo(ahoraS * 1000);
        verify(hardwareService).actualizarHeartbeat("MZ-1", "aa:bb", 90, -60, ahoraS * 1000);
    }

    @Test
    @DisplayName("milisegundos del simulador: se dejan tal cual")
    void milisegundosSeDejan() {
        long ahoraMs = System.currentTimeMillis();
        zona.setLastReadingTime(null);

        service.updateTelemetry("MZ-1", payload(ahoraMs));

        assertThat(zona.getLastReadingTime()).isEqualTo(ahoraMs);
        verify(hardwareService).actualizarHeartbeat("MZ-1", "aa:bb", 90, -60, ahoraMs);
    }

    @Test
    @DisplayName("segundos desde el arranque (sin NTP): se usa la hora de recepción")
    void sinNtpUsaLaHoraDeRecepcion() {
        long antes = System.currentTimeMillis();
        zona.setLastReadingTime(null);

        service.updateTelemetry("MZ-1", payload(4_200L));

        assertThat(zona.getLastReadingTime()).isBetween(antes, System.currentTimeMillis());
        ArgumentCaptor<Long> ts = ArgumentCaptor.forClass(Long.class);
        verify(hardwareService).actualizarHeartbeat(eq("MZ-1"), any(), anyInt(), anyInt(), ts.capture());
        assertThat(ts.getValue()).isEqualTo(zona.getLastReadingTime());
    }

    @Test
    @DisplayName("timestamp ausente: se usa la hora de recepción")
    void ausenteUsaLaHoraDeRecepcion() {
        long antes = System.currentTimeMillis();
        zona.setLastReadingTime(null);

        service.updateTelemetry("MZ-1", payload(null));

        assertThat(zona.getLastReadingTime()).isBetween(antes, System.currentTimeMillis());
    }

    @Test
    @DisplayName("lectura vieja del buffer offline: no pisa la más nueva ni toca estado, motor ni heartbeat")
    void lecturaVieja_seIgnora() {
        long reciente = System.currentTimeMillis() - 5_000;
        zona.setLastReadingTime(reciente);
        zona.setHumSusRaw(55.0);
        long vieja = (System.currentTimeMillis() / 1000) - 3_600; // hace una hora, en segundos

        service.updateTelemetry("MZ-1", payload(vieja));

        assertThat(zona.getLastReadingTime()).isEqualTo(reciente);
        assertThat(zona.getHumSusRaw()).isEqualTo(55.0);
        verify(zonaRepository, never()).save(any());
        verify(actionExecutor, never()).execute(any(), any(), any());
        verify(hardwareService, never()).actualizarHeartbeat(any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("una lectura posterior a la guardada sí se procesa")
    void lecturaMasNueva_seProcesa() {
        zona.setLastReadingTime(System.currentTimeMillis() - 60_000);
        long ahoraS = System.currentTimeMillis() / 1000;

        service.updateTelemetry("MZ-1", payload(ahoraS));

        assertThat(zona.getLastReadingTime()).isCloseTo(ahoraS * 1000, within(1L));
        verify(zonaRepository).save(zona);
    }

    @Test
    @DisplayName("la humedad de sustrato recibida deja su propia marca de tiempo (4.1)")
    void conHumSus_escribeHumSusTs() {
        long ahoraMs = System.currentTimeMillis();
        zona.setLastReadingTime(null);
        zona.setHumSusTs(null);

        service.updateTelemetry("MZ-1", payload(ahoraMs));

        assertThat(zona.getHumSusTs()).isEqualTo(ahoraMs);
    }

    @Test
    @DisplayName("una lectura sin humSus conserva la marca anterior de la humedad (4.1)")
    void sinHumSus_noTocaHumSusTs() {
        long antes = System.currentTimeMillis() - 300_000;
        zona.setLastReadingTime(antes);
        zona.setHumSusRaw(41.0);
        zona.setHumSusTs(antes);
        long ahoraMs = System.currentTimeMillis();

        service.updateTelemetry("MZ-1", new MqttTelemetryPayload("aa:bb", 90, -60, ahoraMs,
                new MqttTelemetryPayload.MetricsPayload(null, 70.0, 22.0, 20.0, 40.0, null, null, null, null, null)));

        assertThat(zona.getLastReadingTime()).isEqualTo(ahoraMs);
        assertThat(zona.getHumSusRaw()).isEqualTo(41.0);
        assertThat(zona.getHumSusTs()).isEqualTo(antes);
    }
}
