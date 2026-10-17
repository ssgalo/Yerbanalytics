package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.lecturas.OrigenLectura;
import com.yerbanalytics.backend.lecturas.RegistroLecturas;
import com.yerbanalytics.backend.model.LecturaZonaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.mqtt.MqttTelemetryReceiver;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.RespuestaPulso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La ingesta guarda una fila del historial por lectura aceptada, en la misma transacción que la zona. */
@DisplayName("NurseryService.updateTelemetry - historial de lecturas")
class NurseryServiceLecturasTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T13:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private ZonaRepository zonaRepository;
    private LecturaZonaRepository lecturaRepository;
    private PulsoSensadoService pulsos;
    private NurseryService service;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        lecturaRepository = mock(LecturaZonaRepository.class);
        pulsos = mock(PulsoSensadoService.class);
        when(pulsos.registrarRespuesta(anyString(), anyLong())).thenReturn(RespuestaPulso.NINGUNA);
        CatalogoParametrosService parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        RuleOrchestrator orquestador = new RuleOrchestrator(List.of(), parametros, new TrazaEvaluacionStore());

        zona = RuleContextTestFactory.zonaBasica("MZ-1");
        zona.setLastReadingTime(null);
        zona.setTempRaw(24.0);
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setZona(zona);
        zona.setSectors(List.of(sector));
        when(zonaRepository.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(sector));

        service = servicioCon(new RegistroLecturas(lecturaRepository), orquestador, sectorRepository);
    }

    private NurseryService servicioCon(RegistroLecturas registro, RuleOrchestrator orquestador,
                                       SectorRepository sectorRepository) {
        return new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository,
                mock(HistorialService.class), mock(ConfiguracionService.class), mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orquestador, mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class), pulsos,
                mock(EscalamientoNodoService.class), registro, RELOJ, 20);
    }

    private static MqttTelemetryPayload payload(long ts, MqttTelemetryPayload.MetricsPayload metricas) {
        return new MqttTelemetryPayload("aa:bb", 90, -60, ts, metricas);
    }

    private LecturaZonaEntity guardada() {
        ArgumentCaptor<LecturaZonaEntity> captor = ArgumentCaptor.forClass(LecturaZonaEntity.class);
        verify(lecturaRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("lectura completa que responde a un pulso: una fila con esos valores y origen PULSO")
    void lecturaCompletaDePulso() {
        when(pulsos.registrarRespuesta(anyString(), anyLong())).thenReturn(new RespuestaPulso(true, true));

        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(), new MqttTelemetryPayload.MetricsPayload(
                38.0, 70.0, 22.0, 20.0, 40.0, 800.0, 5.5, 150.0, 45.0, 180.0)));

        LecturaZonaEntity fila = guardada();
        assertThat(fila.getZonaId()).isEqualTo("MZ-1");
        assertThat(fila.getTs()).isEqualTo(AHORA.toEpochMilli());
        assertThat(fila.getRecibidaTs()).isEqualTo(AHORA.toEpochMilli());
        assertThat(fila.getOrigen()).isEqualTo(OrigenLectura.PULSO);
        assertThat(fila.getHumSus()).isEqualTo(38.0);
        assertThat(fila.getK()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("un pulso sin motor también es origen PULSO")
    void pulsoSinMotorTambienEsPulso() {
        when(pulsos.registrarRespuesta(anyString(), anyLong())).thenReturn(new RespuestaPulso(true, false));

        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        assertThat(guardada().getOrigen()).isEqualTo(OrigenLectura.PULSO);
    }

    @Test
    @DisplayName("fuera de un pulso (manual, verificación, arranque) el origen es OTRA")
    void fueraDePulsoEsOtra() {
        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        assertThat(guardada().getOrigen()).isEqualTo(OrigenLectura.OTRA);
    }

    @Test
    @DisplayName("lectura parcial: la fila guarda la métrica nueva y el valor vigente de las demás")
    void lecturaParcialConservaLoVigente() {
        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(31.0, null, null, null, null, null, null, null, null, null)));

        LecturaZonaEntity fila = guardada();
        assertThat(fila.getHumSus()).isEqualTo(31.0);
        assertThat(fila.getTemp()).as("la temperatura vigente era 24 °C").isEqualTo(24.0);
    }

    @Test
    @DisplayName("la CE llega en µS/cm y la fila la guarda en dS/m")
    void ceEnDsPorM() {
        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, null, null, null, null, 800.0, null, null, null, null)));

        assertThat(guardada().getCe()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("una lectura anterior a la última guardada no entra al historial")
    void lecturaVieja() {
        zona.setLastReadingTime(AHORA.toEpochMilli());

        service.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli() - 60_000L,
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        verify(lecturaRepository, never()).save(any());
    }

    @Test
    @DisplayName("si falla el guardado del historial la excepción sale y la zona no se guarda")
    void siFallaElHistorialLaZonaNoSeAplica() {
        RegistroLecturas registro = mock(RegistroLecturas.class);
        doThrow(new IllegalStateException("base caída")).when(registro).registrar(any(), anyLong(), anyLong(), any());
        NurseryService fragil = servicioCon(registro,
                new RuleOrchestrator(List.of(), mock(CatalogoParametrosService.class), new TrazaEvaluacionStore()),
                mock(SectorRepository.class));

        assertThatThrownBy(() -> fragil.updateTelemetry("MZ-1", payload(AHORA.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
        verify(zonaRepository, never()).save(zona);
    }

    @Test
    @DisplayName("el JSON del contrato (como lo publica el simulador) llega por el receptor MQTT hasta la serie")
    void elPayloadDelContratoAlimentaLaSerie() {
        String json = "{\"mac\":\"AA:BB:CC\",\"battery\":88,\"signal\":-61,\"timestamp\":" + AHORA.toEpochMilli()
                + ",\"metrics\":{\"humSus\":41.5,\"humAmb\":70,\"temp\":23.5,\"tempSuelo\":19,\"uv\":52,"
                + "\"ce\":1200,\"phSuelo\":5.4,\"n\":150,\"p\":45,\"k\":180,\"salinidad\":600}}";
        Message<String> mensaje = MessageBuilder.withPayload(json)
                .setHeader("mqtt_receivedTopic", "nursery/zone/MZ-1/telemetry").build();

        new MqttTelemetryReceiver(service).processMessage(mensaje);

        LecturaZonaEntity fila = guardada();
        assertThat(fila.getZonaId()).isEqualTo("MZ-1");
        assertThat(fila.getHumSus()).isEqualTo(41.5);
        assertThat(fila.getUv()).isEqualTo(52.0);
        assertThat(fila.getCe()).as("1200 µS/cm = 1,2 dS/m").isEqualTo(1.2);
        assertThat(fila.getK()).isEqualTo(180.0);
    }
}
