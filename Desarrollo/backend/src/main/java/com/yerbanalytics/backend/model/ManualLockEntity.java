package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Manual override lock that suspends autonomous actuation for a sector or zone (HU-19).
 *
 * <p>When an operator activates a manual lock, {@code ManualLockRule} detects this active
 * record and emits {@code ABORT_ALL}, stopping all autonomous actuation for the affected
 * sector or zone.
 *
 * <p>A lock with {@code sectorId = null} applies to the entire zone (zone-level lock).
 * A lock with {@code zonaId = null} applies only to the indicated sector.
 *
 * <p>The table is created by Hibernate via {@code ddl-auto=update}.
 */
@Entity
@Table(name = "bloqueo_manual")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManualLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the locked sector; {@code null} if the lock applies to the entire zone. */
    @Column(name = "sector_id")
    private String sectorId;

    /** ID of the locked zone; {@code null} if the lock applies only to the sector. */
    @Column(name = "zona_id")
    private String zonaId;

    /** User or role that activated the lock (e.g. "Agrónomo", "admin"). */
    @Column(name = "activado_por", nullable = false)
    private String activatedBy;

    /** Epoch ms when the lock was activated. */
    @Column(name = "activado_ts", nullable = false)
    private Long activatedAt;

    /** Human-readable reason for the lock, persisted in the history. */
    @Column(nullable = false)
    private String reason;

    /**
     * {@code true} while the lock is active. Set to {@code false} when the operator
     * manually deactivates it.
     */
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;
}
