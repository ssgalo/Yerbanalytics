package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.HistorialEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialRepository extends JpaRepository<HistorialEventoEntity, String> {

    /** Eventos que cumplen los filtros opcionales (null = sin filtrar), más recientes primero. */
    @Query("""
            SELECT h FROM HistorialEventoEntity h
            WHERE (:sector IS NULL OR h.sectorId = :sector)
              AND (:zona   IS NULL OR h.zonaId   = :zona)
              AND (:tipo   IS NULL OR h.tipo     = :tipo)
              AND (:desde  IS NULL OR h.ts >= :desde)
              AND (:hasta  IS NULL OR h.ts <= :hasta)
            ORDER BY h.ts DESC
            """)
    List<HistorialEventoEntity> findFiltered(
            @Param("sector") String sector,
            @Param("zona") String zona,
            @Param("tipo") String tipo,
            @Param("desde") Long desde,
            @Param("hasta") Long hasta);

    /** Eventos con seguimiento pendiente de evaluar (latencia aún no resuelta). */
    List<HistorialEventoEntity> findByEvoShowTrueAndEvoEvaluadoTsIsNull();
}
