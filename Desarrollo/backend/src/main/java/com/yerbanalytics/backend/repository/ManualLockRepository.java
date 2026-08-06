package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ManualLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for manual override locks (HU-19).
 *
 * <p>All queries filter by {@code active = true}: deactivated locks are retained
 * in the table for auditing but are invisible to the rules engine.
 */
@Repository
public interface ManualLockRepository extends JpaRepository<ManualLockEntity, Long> {

    /** Active locks for a specific sector. */
    List<ManualLockEntity> findBySectorIdAndActiveTrue(String sectorId);

    /** Active locks for an entire zone (apply to all its sectors). */
    List<ManualLockEntity> findByZonaIdAndActiveTrue(String zonaId);
}
