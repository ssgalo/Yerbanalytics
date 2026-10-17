package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre contra la PostgreSQL de dev con rollback por test (no deja filas). Las zonas son ficticias
 * (prefijo {@code ZT-}) para no mezclarse con las reales.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ZoneReadingRepository")
class ZoneReadingRepositoryTest {

    private static final String ZONE = "ZT-1";

    @Autowired
    private ZoneReadingRepository repository;

    static ZoneReadingEntity reading(String zone, long ts, ReadingOrigin origin) {
        ZoneReadingEntity r = new ZoneReadingEntity();
        r.setZoneId(zone);
        r.setTs(ts);
        r.setReceivedTs(ts + 50);
        r.setOrigin(origin);
        r.setHumSus(48.0);
        r.setHumAmb(72.0);
        r.setTemp(23.5);
        r.setTempSuelo(20.0);
        r.setUv(55.0);
        r.setCe(0.8);
        r.setPhSuelo(5.6);
        r.setN(150.0);
        r.setP(45.0);
        r.setK(180.0);
        return r;
    }

    @Test
    @DisplayName("guarda y lee una lectura con las diez métricas, dpv nulo y su origen")
    void savesAndReads() {
        ZoneReadingEntity saved = repository.saveAndFlush(reading(ZONE, 1_000L, ReadingOrigin.PULSE));

        ZoneReadingEntity read = repository.findById(saved.getId()).orElseThrow();

        assertThat(read.getZoneId()).isEqualTo(ZONE);
        assertThat(read.getTs()).isEqualTo(1_000L);
        assertThat(read.getReceivedTs()).isEqualTo(1_050L);
        assertThat(read.getOrigin()).isEqualTo(ReadingOrigin.PULSE);
        assertThat(read.getHumSus()).isEqualTo(48.0);
        assertThat(read.getCe()).isEqualTo(0.8);
        assertThat(read.getK()).isEqualTo(180.0);
        assertThat(read.getDpv()).isNull();
    }

    @Test
    @DisplayName("las últimas N de una zona salen de la más nueva a la más vieja y no mezclan zonas")
    void latestByZone() {
        repository.save(reading(ZONE, 1_000L, ReadingOrigin.PULSE));
        repository.save(reading(ZONE, 3_000L, ReadingOrigin.OTHER));
        repository.save(reading(ZONE, 2_000L, ReadingOrigin.PULSE));
        repository.save(reading("ZT-2", 4_000L, ReadingOrigin.PULSE));

        List<ZoneReadingEntity> all = repository.findByZoneIdOrderByTsDesc(ZONE, PageRequest.of(0, 2));
        List<ZoneReadingEntity> pulseOnly =
                repository.findByZoneIdAndOriginOrderByTsDesc(ZONE, ReadingOrigin.PULSE, PageRequest.of(0, 5));

        assertThat(all).extracting(ZoneReadingEntity::getTs).containsExactly(3_000L, 2_000L);
        assertThat(pulseOnly).extracting(ZoneReadingEntity::getTs).containsExactly(2_000L, 1_000L);
    }

    @Test
    @DisplayName("la ventana devuelve las lecturas con ts en [desde, hasta]")
    void window() {
        repository.save(reading(ZONE, 1_000L, ReadingOrigin.PULSE));
        repository.save(reading(ZONE, 2_000L, ReadingOrigin.PULSE));
        repository.save(reading(ZONE, 3_000L, ReadingOrigin.PULSE));

        List<ZoneReadingEntity> window = repository.findByZoneIdAndTsBetweenOrderByTsAsc(ZONE, 2_000L, 3_000L);

        assertThat(window).extracting(ZoneReadingEntity::getTs).containsExactly(2_000L, 3_000L);
    }

    @Test
    @DisplayName("la purga borra lo anterior al corte y respeta lo nuevo")
    void purge() {
        repository.save(reading(ZONE, 1_000L, ReadingOrigin.PULSE));
        repository.save(reading(ZONE, 5_000L, ReadingOrigin.PULSE));

        long deleted = repository.deleteByTsLessThan(3_000L);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByZoneIdAndTsBetweenOrderByTsAsc(ZONE, 0L, 10_000L))
                .extracting(ZoneReadingEntity::getTs).containsExactly(5_000L);
    }
}
