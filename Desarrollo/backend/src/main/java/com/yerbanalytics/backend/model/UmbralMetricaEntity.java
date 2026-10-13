package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bandas configurables de una métrica (HU-15). Sólo se persisten los límites
 * ideal/warn/crit; los metadatos (label, unit, dec, base) viven en
 * {@code NurseryConstants.SPECS} y no se duplican aquí.
 */
@Entity
@Table(name = "umbral_metrica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UmbralMetricaEntity {

    /** Clave de la métrica; una de las 10 de {@code NurseryConstants.SPECS}. */
    @Id
    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "ideal_min", nullable = false)
    private double idealMin;

    @Column(name = "ideal_max", nullable = false)
    private double idealMax;

    @Column(name = "warn_min", nullable = false)
    private double warnMin;

    @Column(name = "warn_max", nullable = false)
    private double warnMax;

    @Column(name = "crit_min", nullable = false)
    private double critMin;

    @Column(name = "crit_max", nullable = false)
    private double critMax;

    /**
     * Punto de alerta dentro de la franja amarilla baja ({@code warnMin ≤ alertMin ≤ idealMin}).
     * Nulable: las filas anteriores a la columna (y las que nunca lo definieron) usan el medio
     * de la franja. No cambia el estado; sólo decide si se emite una alerta.
     */
    @Column(name = "alert_min")
    private Double alertMin;

    /** Punto de alerta dentro de la franja amarilla alta ({@code idealMax ≤ alertMax ≤ warnMax}). */
    @Column(name = "alert_max")
    private Double alertMax;
}
