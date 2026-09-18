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

    /**
     * All active locks in a single query.
     *
     * <p>Used by the proactive watchdog sweep: iterating 600 sectors and querying per-sector
     * and per-zone would be 1200 queries per cycle. Active locks are few and fit comfortably
     * in memory as two sets.
     */
    List<ManualLockEntity> findByActiveTrue();
}

