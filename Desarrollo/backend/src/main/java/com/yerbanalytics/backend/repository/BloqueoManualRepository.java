package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.BloqueoManualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para bloqueos manuales de actuación autónoma (HU-19).
 *
 * <p>Las consultas filtran siempre por {@code activo = true}: los bloqueos
 * desactivados se conservan en la tabla para auditoría pero no afectan al motor.
 */
@Repository
public interface BloqueoManualRepository extends JpaRepository<BloqueoManualEntity, Long> {

    /** Bloqueos activos para un sector específico. */
    List<BloqueoManualEntity> findBySectorIdAndActivoTrue(String sectorId);

    /** Bloqueos activos para una zona completa (aplican a todos sus sectores). */
    List<BloqueoManualEntity> findByZonaIdAndActivoTrue(String zonaId);

    /**
     * Todos los bloqueos activos, de una sola consulta.
     *
     * <p>Para el barrido proactivo del watchdog, que recorre los 600 sectores: preguntar por
     * sector y por zona uno por uno son 1200 consultas por ciclo, y el ciclo comparte hilo con
     * el keep-alive del canal SSE de las cámaras. Los bloqueos activos son un puñado — entran
     * holgados en memoria y se consultan como conjuntos.
     */
    List<BloqueoManualEntity> findByActivoTrue();
}
