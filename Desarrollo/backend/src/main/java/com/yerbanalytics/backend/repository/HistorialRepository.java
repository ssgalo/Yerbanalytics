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

    /**
     * Los {@code limit} eventos de acción más recientes (excluye tipos Info y Configuración),
     * ordenados del más reciente al más antiguo. Usado por {@code NurseryService} para
     * poblar el feed de actividad del panel general con datos reales.
     */
    @Query("""
            SELECT h FROM HistorialEventoEntity h
            WHERE h.tipo NOT IN ('Info', 'Configuraci\u00f3n')
            ORDER BY h.ts DESC
            LIMIT :limit
            """)
    List<HistorialEventoEntity> findRecentActions(@Param("limit") int limit);

    /**
     * Cuenta los eventos de un tipo desde un timestamp dado (para los KPIs diarios).
     * Los tipos INFO y Configuraci\u00f3n se excluyen; solo cuentan las acciones reales.
     *
     * @param tipo  tipo de evento (ej. {@code "Riego"}, {@code "Insumo"})
     * @param desde timestamp de inicio del per\u00edodo (epoch ms, inicio del d\u00eda local)
     * @return cantidad de eventos del tipo en el per\u00edodo
     */
    @Query("""
            SELECT COUNT(h) FROM HistorialEventoEntity h
            WHERE h.tipo = :tipo
              AND h.ts >= :desde
            """)
    long countByTipoSinceTs(
            @Param("tipo") String tipo,
            @Param("desde") long desde);

    /**
     * Fila de {@link #ultimosPorSector}: el último evento de un sector por tipo y regla.
     * {@code regla} es {@code null} en los eventos sin regla (p. ej. las aplicaciones de insumo).
     */
    interface UltimoEvento {
        String getSectorId();

        String getTipo();

        String getRegla();

        Long getTs();
    }

    /**
     * Lo último que le pasó a cada sector de una zona (riegos e insumos desde {@code desde}), en
     * UNA consulta: el contexto de riego se arma por zona, no por sector. El último riego de un
     * sector es el mayor {@code ts} de sus filas "Riego"; el último ordenado por R-02, el de la fila
     * con {@code regla = DeficitCriticoRule}.
     */
    @Query("""
            SELECT h.sectorId AS sectorId, h.tipo AS tipo, h.regla AS regla, MAX(h.ts) AS ts
            FROM HistorialEventoEntity h
            WHERE h.zonaId = :zonaId
              AND h.tipo IN ('Riego', 'Insumo')
              AND h.ts >= :desde
            GROUP BY h.sectorId, h.tipo, h.regla
            """)
    List<UltimoEvento> ultimosPorSector(@Param("zonaId") String zonaId, @Param("desde") long desde);

    /**
     * Riegos despachados con duración registrada desde {@code desde}: con ellos el despacho
     * reconstruye, tras un reinicio, qué válvulas siguen abiertas.
     */
    @Query("""
            SELECT h FROM HistorialEventoEntity h
            WHERE h.tipo = 'Riego'
              AND h.duracionSeg IS NOT NULL
              AND h.ts >= :desde
            """)
    List<HistorialEventoEntity> riegosDesde(@Param("desde") long desde);
}
