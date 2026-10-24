package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
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
import com.yerbanalytics.backend.readings.ReadingOrigin;
import com.yerbanalytics.backend.readings.ReadingRecorder;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.mqtt.MqttTelemetryReceiver;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulseResponse;
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
class NurseryServiceReadingsTest {

    private static final Instant NOW = Instant.parse("2026-10-14T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZonaHorariaVivero.ZONA);

    private ZonaRepository zoneRepository;
    private ZoneReadingRepository readingRepository;
    private PulsoSensadoService pulses;
    private NurseryService service;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ZonaRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        readingRepository = mock(ZoneReadingRepository.class);
        pulses = mock(PulsoSensadoService.class);
        when(pulses.recordResponse(anyString(), anyLong())).thenReturn(PulseResponse.NONE);
        CatalogoParametrosService parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        RuleOrchestrator orchestrator = new RuleOrchestrator(List.of(), parameters, new TrazaEvaluacionStore());

        zone = RuleContextTestFactory.zonaBasica("MZ-1");
        zone.setLastReadingTime(null);
        zone.setTempRaw(24.0);
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setZona(zone);
        zone.setSectors(List.of(sector));
        when(zoneRepository.findById("MZ-1")).thenReturn(Optional.of(zone));
        when(sectorRepository.findByZonaId("MZ-1")).thenReturn(List.of(sector));

        service = serviceWith(new ReadingRecorder(readingRepository), orchestrator, sectorRepository);
    }

    private NurseryService serviceWith(ReadingRecorder recorder, RuleOrchestrator orchestrator,
                                       SectorRepository sectorRepository) {
        return new NurseryService(new NurseryProperties(), zoneRepository, sectorRepository,
                mock(HistorialService.class), mock(ConfiguracionService.class), mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class), orchestrator, mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class), pulses,
                mock(EscalamientoNodoService.class), recorder, ReadingGuardsDePrueba.aceptaTodo(), CLOCK, 20);
    }

    private static MqttTelemetryPayload payload(long ts, MqttTelemetryPayload.MetricsPayload metrics) {
        return new MqttTelemetryPayload("aa:bb", 90, -60, ts, metrics);
    }

    private ZoneReadingEntity saved() {
        ArgumentCaptor<ZoneReadingEntity> captor = ArgumentCaptor.forClass(ZoneReadingEntity.class);
        verify(readingRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("lectura completa que responde a un pulso: una fila con esos valores y origen PULSO")
    void fullReadingOfAPulse() {
        when(pulses.recordResponse(anyString(), anyLong())).thenReturn(new PulseResponse(true, true));

        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(), new MqttTelemetryPayload.MetricsPayload(
                38.0, 70.0, 22.0, 20.0, 40.0, 800.0, 5.5, 150.0, 45.0, 180.0)));

        ZoneReadingEntity row = saved();
        assertThat(row.getZoneId()).isEqualTo("MZ-1");
        assertThat(row.getTs()).isEqualTo(NOW.toEpochMilli());
        assertThat(row.getReceivedTs()).isEqualTo(NOW.toEpochMilli());
        assertThat(row.getOrigin()).isEqualTo(ReadingOrigin.PULSE);
        assertThat(row.getHumSus()).isEqualTo(38.0);
        assertThat(row.getK()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("un pulso sin motor también es origen PULSO")
    void pulseWithoutEngineIsStillPulse() {
        when(pulses.recordResponse(anyString(), anyLong())).thenReturn(new PulseResponse(true, false));

        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        assertThat(saved().getOrigin()).isEqualTo(ReadingOrigin.PULSE);
    }

    @Test
    @DisplayName("fuera de un pulso (manual, verificación, arranque) el origen es OTRA")
    void outsideAPulseIsOther() {
        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        assertThat(saved().getOrigin()).isEqualTo(ReadingOrigin.OTHER);
    }

    @Test
    @DisplayName("lectura parcial: la fila guarda la métrica nueva y el valor vigente de las demás")
    void partialReadingKeepsTheCurrentValues() {
        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(31.0, null, null, null, null, null, null, null, null, null)));

        ZoneReadingEntity row = saved();
        assertThat(row.getHumSus()).isEqualTo(31.0);
        assertThat(row.getTemp()).as("la temperatura vigente era 24 °C").isEqualTo(24.0);
    }

    @Test
    @DisplayName("una lectura con temperatura y humedad ambiental guarda el DPV en la zona y en la fila")
    void readingWithTempAndHumiditySavesTheDpv() {
        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, 60.0, 25.0, null, null, null, null, null, null, null)));

        assertThat(zone.getDpvRaw()).isEqualTo(1.27);
        assertThat(zone.getDpvTs()).isEqualTo(NOW.toEpochMilli());
        assertThat(saved().getDpv()).isEqualTo(1.27);
    }

    @Test
    @DisplayName("sin temperatura en la lectura el DPV conserva su valor y su marca de tiempo")
    void readingWithoutTemperatureKeepsThePreviousDpv() {
        zone.setDpvRaw(1.5);
        zone.setDpvTs(1_000L);

        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, 40.0, null, null, null, null, null, null, null, null)));

        assertThat(zone.getDpvRaw()).isEqualTo(1.5);
        assertThat(zone.getDpvTs()).isEqualTo(1_000L);
        assertThat(saved().getDpv()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("sin humedad ambiental en la lectura el DPV conserva su valor y su marca de tiempo")
    void readingWithoutHumidityKeepsThePreviousDpv() {
        zone.setDpvRaw(1.5);
        zone.setDpvTs(1_000L);

        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, null, 30.0, null, null, null, null, null, null, null)));

        assertThat(zone.getDpvRaw()).isEqualTo(1.5);
        assertThat(zone.getDpvTs()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("una humedad fuera de rango no genera un DPV nuevo")
    void outOfRangeHumidityDoesNotComputeADpv() {
        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, 130.0, 25.0, null, null, null, null, null, null, null)));

        assertThat(zone.getDpvRaw()).isNull();
    }

    @Test
    @DisplayName("la CE llega en µS/cm y la fila la guarda en dS/m")
    void ceInDsPerM() {
        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(null, null, null, null, null, 800.0, null, null, null, null)));

        assertThat(saved().getCe()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("una lectura anterior a la última guardada no entra al historial")
    void oldReading() {
        zone.setLastReadingTime(NOW.toEpochMilli());

        service.updateTelemetry("MZ-1", payload(NOW.toEpochMilli() - 60_000L,
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null)));

        verify(readingRepository, never()).save(any());
    }

    @Test
    @DisplayName("si falla el guardado del historial la excepción sale y la zona no se guarda")
    void ifTheHistoryFailsTheZoneIsNotApplied() {
        ReadingRecorder recorder = mock(ReadingRecorder.class);
        doThrow(new IllegalStateException("base caída")).when(recorder).record(any(), anyLong(), anyLong(), any());
        NurseryService fragile = serviceWith(recorder,
                new RuleOrchestrator(List.of(), mock(CatalogoParametrosService.class), new TrazaEvaluacionStore()),
                mock(SectorRepository.class));

        assertThatThrownBy(() -> fragile.updateTelemetry("MZ-1", payload(NOW.toEpochMilli(),
                new MqttTelemetryPayload.MetricsPayload(38.0, null, null, null, null, null, null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
        verify(zoneRepository, never()).save(zone);
    }

    @Test
    @DisplayName("la telemetría del simulador con 38 °C y 25 % de humedad ambiental da un DPV de 4,97 kPa (calor seco)")
    void simulatorTelemetryYieldsTheExpectedDpv() {
        String json = "{\"mac\":\"AA:BB:CC\",\"battery\":88,\"signal\":-61,\"timestamp\":" + NOW.toEpochMilli()
                + ",\"metrics\":{\"humAmb\":25,\"temp\":38}}";
        Message<String> message = MessageBuilder.withPayload(json)
                .setHeader("mqtt_receivedTopic", "nursery/zone/MZ-1/telemetry").build();

        new MqttTelemetryReceiver(service).processMessage(message);

        assertThat(saved().getDpv()).isEqualTo(4.97);
    }

    @Test
    @DisplayName("el JSON del contrato (como lo publica el simulador) llega por el receptor MQTT hasta la serie")
    void theContractPayloadFeedsTheSeries() {
        String json = "{\"mac\":\"AA:BB:CC\",\"battery\":88,\"signal\":-61,\"timestamp\":" + NOW.toEpochMilli()
                + ",\"metrics\":{\"humSus\":41.5,\"humAmb\":70,\"temp\":23.5,\"tempSuelo\":19,\"uv\":52,"
                + "\"ce\":1200,\"phSuelo\":5.4,\"n\":150,\"p\":45,\"k\":180,\"salinidad\":600}}";
        Message<String> message = MessageBuilder.withPayload(json)
                .setHeader("mqtt_receivedTopic", "nursery/zone/MZ-1/telemetry").build();

        new MqttTelemetryReceiver(service).processMessage(message);

        ZoneReadingEntity row = saved();
        assertThat(row.getZoneId()).isEqualTo("MZ-1");
        assertThat(row.getHumSus()).isEqualTo(41.5);
        assertThat(row.getUv()).isEqualTo(52.0);
        assertThat(row.getCe()).as("1200 µS/cm = 1,2 dS/m").isEqualTo(1.2);
        assertThat(row.getK()).isEqualTo(180.0);
    }
}
