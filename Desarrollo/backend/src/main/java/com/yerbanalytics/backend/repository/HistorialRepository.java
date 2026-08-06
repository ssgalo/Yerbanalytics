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

    /**
     * Cuenta los eventos de un tipo determinado para un sector en los últimos {@code desde} ms.
     * Usado por {@code DosisLimiteRule} para verificar el límite de dosis en 24 h sin
     * traer los registros completos.
     *
     * @param sectorId ID del sector
     * @param tipo     tipo de evento (ej. {@code "Insumo"})
     * @param desde    timestamp de inicio del período (epoch ms)
     * @return cantidad de eventos del tipo en el período
     */
    @Query("""
            SELECT COUNT(h) FROM HistorialEventoEntity h
            WHERE h.sectorId = :sectorId
              AND h.tipo = :tipo
              AND h.ts >= :desde
            """)
    long countByTipoAndSectorAndPeriod(
            @Param("sectorId") String sectorId,
            @Param("tipo") String tipo,
            @Param("desde") long desde);
}
