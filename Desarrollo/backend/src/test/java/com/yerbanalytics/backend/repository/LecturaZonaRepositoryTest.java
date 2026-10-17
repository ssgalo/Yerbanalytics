package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.lecturas.OrigenLectura;
import com.yerbanalytics.backend.model.LecturaZonaEntity;
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
@DisplayName("LecturaZonaRepository")
class LecturaZonaRepositoryTest {

    private static final String ZONA = "ZT-1";

    @Autowired
    private LecturaZonaRepository repository;

    static LecturaZonaEntity lectura(String zona, long ts, OrigenLectura origen) {
        LecturaZonaEntity l = new LecturaZonaEntity();
        l.setZonaId(zona);
        l.setTs(ts);
        l.setRecibidaTs(ts + 50);
        l.setOrigen(origen);
        l.setHumSus(48.0);
        l.setHumAmb(72.0);
        l.setTemp(23.5);
        l.setTempSuelo(20.0);
        l.setUv(55.0);
        l.setCe(0.8);
        l.setPhSuelo(5.6);
        l.setN(150.0);
        l.setP(45.0);
        l.setK(180.0);
        return l;
    }

    @Test
    @DisplayName("guarda y lee una lectura con las diez métricas, dpv nulo y su origen")
    void guardaYLee() {
        LecturaZonaEntity guardada = repository.saveAndFlush(lectura(ZONA, 1_000L, OrigenLectura.PULSO));

        LecturaZonaEntity leida = repository.findById(guardada.getId()).orElseThrow();

        assertThat(leida.getZonaId()).isEqualTo(ZONA);
        assertThat(leida.getTs()).isEqualTo(1_000L);
        assertThat(leida.getRecibidaTs()).isEqualTo(1_050L);
        assertThat(leida.getOrigen()).isEqualTo(OrigenLectura.PULSO);
        assertThat(leida.getHumSus()).isEqualTo(48.0);
        assertThat(leida.getCe()).isEqualTo(0.8);
        assertThat(leida.getK()).isEqualTo(180.0);
        assertThat(leida.getDpv()).isNull();
    }

    @Test
    @DisplayName("las últimas N de una zona salen de la más nueva a la más vieja y no mezclan zonas")
    void ultimasPorZona() {
        repository.save(lectura(ZONA, 1_000L, OrigenLectura.PULSO));
        repository.save(lectura(ZONA, 3_000L, OrigenLectura.OTRA));
        repository.save(lectura(ZONA, 2_000L, OrigenLectura.PULSO));
        repository.save(lectura("ZT-2", 4_000L, OrigenLectura.PULSO));

        List<LecturaZonaEntity> todas = repository.findByZonaIdOrderByTsDesc(ZONA, PageRequest.of(0, 2));
        List<LecturaZonaEntity> soloPulso =
                repository.findByZonaIdAndOrigenOrderByTsDesc(ZONA, OrigenLectura.PULSO, PageRequest.of(0, 5));

        assertThat(todas).extracting(LecturaZonaEntity::getTs).containsExactly(3_000L, 2_000L);
        assertThat(soloPulso).extracting(LecturaZonaEntity::getTs).containsExactly(2_000L, 1_000L);
    }

    @Test
    @DisplayName("la ventana devuelve las lecturas con ts en [desde, hasta]")
    void ventana() {
        repository.save(lectura(ZONA, 1_000L, OrigenLectura.PULSO));
        repository.save(lectura(ZONA, 2_000L, OrigenLectura.PULSO));
        repository.save(lectura(ZONA, 3_000L, OrigenLectura.PULSO));

        List<LecturaZonaEntity> ventana = repository.findByZonaIdAndTsBetweenOrderByTsAsc(ZONA, 2_000L, 3_000L);

        assertThat(ventana).extracting(LecturaZonaEntity::getTs).containsExactly(2_000L, 3_000L);
    }

    @Test
    @DisplayName("la purga borra lo anterior al corte y respeta lo nuevo")
    void purga() {
        repository.save(lectura(ZONA, 1_000L, OrigenLectura.PULSO));
        repository.save(lectura(ZONA, 5_000L, OrigenLectura.PULSO));

        long borradas = repository.deleteByTsLessThan(3_000L);

        assertThat(borradas).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByZonaIdAndTsBetweenOrderByTsAsc(ZONA, 0L, 10_000L))
                .extracting(LecturaZonaEntity::getTs).containsExactly(5_000L);
    }
}
