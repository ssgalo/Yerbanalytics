package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.lecturas.OrigenLectura;
import com.yerbanalytics.backend.model.LecturaZonaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Historial de lecturas por macro-zona. Toda consulta es acotada: por zona y por rango de tiempo, o las últimas N
 * (con {@link Pageable}); nunca se trae la tabla entera.
 */
@Repository
public interface LecturaZonaRepository extends JpaRepository<LecturaZonaEntity, Long> {

    /** Las más nuevas primero; el tamaño de página es el N pedido. */
    List<LecturaZonaEntity> findByZonaIdOrderByTsDesc(String zonaId, Pageable pagina);

    List<LecturaZonaEntity> findByZonaIdAndOrigenOrderByTsDesc(String zonaId, OrigenLectura origen, Pageable pagina);

    /** Lecturas con {@code desde <= ts <= hasta}, de la más vieja a la más nueva. */
    List<LecturaZonaEntity> findByZonaIdAndTsBetweenOrderByTsAsc(String zonaId, long desde, long hasta);

    /** Borra las anteriores al corte (epoch ms) y devuelve cuántas. */
    @Modifying
    @Query("delete from LecturaZonaEntity l where l.ts < :corte")
    int deleteByTsLessThan(@Param("corte") long corte);
}
