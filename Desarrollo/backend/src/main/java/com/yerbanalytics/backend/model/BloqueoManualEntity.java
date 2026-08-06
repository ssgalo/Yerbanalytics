package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bloqueo manual de actuación autónoma para un sector o zona (HU-19).
 *
 * <p>Cuando un operario activa un bloqueo manual, el motor de reglas ({@code BloqueoManualRule})
 * detecta la presencia de este registro activo y emite {@code ABORT_ALL}, deteniendo
 * toda actuación autónoma sobre el sector o zona afectada.
 *
 * <p>Un bloqueo con {@code sectorId = null} aplica a toda la zona (bloqueo zonal).
 * Un bloqueo con {@code zonaId = null} aplica solo al sector indicado.
 *
 * <p>La tabla es creada por Hibernate via {@code ddl-auto=update} (patrón real del repo).
 */
@Entity
@Table(name = "bloqueo_manual")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoManualEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del sector bloqueado; {@code null} si el bloqueo aplica a toda la zona. */
    @Column(name = "sector_id")
    private String sectorId;

    /** ID de la zona bloqueada; {@code null} si el bloqueo aplica solo al sector. */
    @Column(name = "zona_id")
    private String zonaId;

    /** Usuario o rol que activó el bloqueo (ej. "Agrónomo", "admin"). */
    @Column(name = "activado_por", nullable = false)
    private String activadoPor;

    /** Epoch ms en que se activó el bloqueo. */
    @Column(name = "activado_ts", nullable = false)
    private Long activadoTs;

    /** Motivo legible del bloqueo (libre), persiste en el historial. */
    @Column(nullable = false)
    private String motivo;

    /**
     * {@code true} mientras el bloqueo está vigente. Se pone en {@code false}
     * cuando el operario lo desactiva manualmente.
     */
    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;
}
