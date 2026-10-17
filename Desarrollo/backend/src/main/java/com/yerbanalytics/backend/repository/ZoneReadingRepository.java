package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingOrigin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Historial de lecturas por macro-zona. Toda consulta es acotada: por zona y por rango de tiempo, o las últimas N
 * (con {@link Pageable}); nunca se trae la tabla entera.
 */
@Repository
public interface ZoneReadingRepository extends JpaRepository<ZoneReadingEntity, Long> {

    /** Las más nuevas primero; el tamaño de página es el N pedido. */
    List<ZoneReadingEntity> findByZoneIdOrderByTsDesc(String zoneId, Pageable page);

    List<ZoneReadingEntity> findByZoneIdAndOriginOrderByTsDesc(String zoneId, ReadingOrigin origin, Pageable page);

    /** Lecturas con {@code from <= ts <= to}, de la más vieja a la más nueva. */
    List<ZoneReadingEntity> findByZoneIdAndTsBetweenOrderByTsAsc(String zoneId, long from, long to);

    /** Borra las anteriores al corte (epoch ms) y devuelve cuántas. */
    @Transactional
    @Modifying
    @Query("delete from ZoneReadingEntity r where r.ts < :cutoff")
    int deleteByTsLessThan(@Param("cutoff") long cutoff);
}
