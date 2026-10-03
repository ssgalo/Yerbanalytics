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
 * Límites operativos que no son umbrales de reglas y parámetros de seguimiento post-acción (HU-15).
 * La apertura máxima de la mediasombra y el tiempo máx. de riego se mudaron al catálogo de reglas.
 * Fila única ({@code id = 1}); la calibración del vivero es global.
 */
@Entity
@Table(name = "configuracion_operativa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionOperativaEntity {

    @Id
    private Integer id;

    // --- Riego (HU-15 CA-04). El tiempo máx. de apertura vive en el catálogo de parámetros de reglas. ---
    @Column(name = "riego_vol_max_diario_ml", nullable = false)
    private double riegoVolMaxDiarioMl;

    // --- Insumos / bomba peristáltica (HU-15 CA-05) ---
    @Column(name = "insumo_dosis_max_24h_ml", nullable = false)
    private double insumoDosisMax24hMl;

    // --- Seguimiento post-acción (HU-15 CA-07) ---
    @Column(name = "seguimiento_latencia_min", nullable = false)
    private int seguimientoLatenciaMin;

    @Column(name = "seguimiento_delta_min", nullable = false)
    private double seguimientoDeltaMin;

    // --- Tiempos y Frecuencias ---
    @Column(name = "intervalo_sensado_minutos")
    private Integer intervaloSensadoMinutos = 240;

    @Column(name = "intervalo_evaluacion_minutos")
    private Integer intervaloEvaluacionMinutos = 5;

    // --- Auditoría (HU-15 CA-02) ---
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ts")
    private Long updatedTs;
}
