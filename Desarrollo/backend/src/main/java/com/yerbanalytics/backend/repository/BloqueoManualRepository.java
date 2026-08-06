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
}
