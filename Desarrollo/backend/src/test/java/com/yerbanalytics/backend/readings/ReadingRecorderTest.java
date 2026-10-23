package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ReadingRecorder")
class ReadingRecorderTest {

    private ZoneReadingRepository repository;
    private ReadingRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = mock(ZoneReadingRepository.class);
        recorder = new ReadingRecorder(repository);
    }

    private static ZonaEntity zoneWithFullReading() {
        ZonaEntity z = new ZonaEntity();
        z.setId("MZ-1");
        z.setHumSusRaw(48.0);
        z.setHumAmbRaw(72.0);
        z.setTempRaw(24.0);
        z.setTempSueloRaw(20.0);
        z.setUvRaw(55.0);
        z.setCeRaw(0.8);
        z.setPhSueloRaw(5.6);
        z.setNRaw(150.0);
        z.setPRaw(45.0);
        z.setKRaw(180.0);
        z.setDpvRaw(0.8);
        return z;
    }

    private ZoneReadingEntity saved() {
        ArgumentCaptor<ZoneReadingEntity> captor = ArgumentCaptor.forClass(ZoneReadingEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("guarda las diez métricas vigentes de la zona con la hora del nodo, la de recepción y el origen")
    void savesTheFullState() {
        recorder.record(zoneWithFullReading(), 1_000L, 1_200L, ReadingOrigin.PULSE);

        ZoneReadingEntity r = saved();
        assertThat(r.getZoneId()).isEqualTo("MZ-1");
        assertThat(r.getTs()).isEqualTo(1_000L);
        assertThat(r.getReceivedTs()).isEqualTo(1_200L);
        assertThat(r.getOrigin()).isEqualTo(ReadingOrigin.PULSE);
        assertThat(r.getHumSus()).isEqualTo(48.0);
        assertThat(r.getHumAmb()).isEqualTo(72.0);
        assertThat(r.getTemp()).isEqualTo(24.0);
        assertThat(r.getTempSuelo()).isEqualTo(20.0);
        assertThat(r.getUv()).isEqualTo(55.0);
        assertThat(r.getCe()).isEqualTo(0.8);
        assertThat(r.getPhSuelo()).isEqualTo(5.6);
        assertThat(r.getN()).isEqualTo(150.0);
        assertThat(r.getP()).isEqualTo(45.0);
        assertThat(r.getK()).isEqualTo(180.0);
        assertThat(r.getDpv()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("una métrica que la zona nunca reportó queda sin dato, no en cero")
    void neverReportedMetricStaysNull() {
        ZonaEntity z = zoneWithFullReading();
        z.setPhSueloRaw(null);

        recorder.record(z, 1_000L, 1_200L, ReadingOrigin.OTHER);

        ZoneReadingEntity r = saved();
        assertThat(r.getPhSuelo()).isNull();
        assertThat(r.getOrigin()).isEqualTo(ReadingOrigin.OTHER);
    }
}
