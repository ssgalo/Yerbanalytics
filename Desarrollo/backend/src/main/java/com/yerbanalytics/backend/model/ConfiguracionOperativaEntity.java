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
 * Límites operativos de actuadores y parámetros de seguimiento post-acción (HU-15).
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

    // --- Riego / electroválvula (HU-15 CA-04) ---
    @Column(name = "riego_tiempo_max_seg", nullable = false)
    private double riegoTiempoMaxSeg;

    @Column(name = "riego_vol_max_diario_ml", nullable = false)
    private double riegoVolMaxDiarioMl;

    // --- Insumos / bomba peristáltica (HU-15 CA-05) ---
    @Column(name = "insumo_dosis_max_24h_ml", nullable = false)
    private double insumoDosisMax24hMl;

    // --- Mediasombra (HU-15 CA-06) ---
    @Column(name = "mediasombra_apertura_max_pct", nullable = false)
    private double mediasombraAperturaMaxPct;

    // --- Seguimiento post-acción (HU-15 CA-07) ---
    @Column(name = "seguimiento_latencia_min", nullable = false)
    private int seguimientoLatenciaMin;

    @Column(name = "seguimiento_delta_min", nullable = false)
    private double seguimientoDeltaMin;

    // --- Auditoría (HU-15 CA-02) ---
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ts")
    private Long updatedTs;
}
