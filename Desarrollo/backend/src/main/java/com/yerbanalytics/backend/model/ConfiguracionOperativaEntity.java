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

    // --- Insumos / bomba peristáltica (HU-15 CA-05) ---
    @Column(name = "insumo_dosis_max_24h_ml", nullable = false)
    private double insumoDosisMax24hMl;

    // --- Seguimiento post-acción (HU-15 CA-07) ---
    @Column(name = "seguimiento_latencia_min", nullable = false)
    private int seguimientoLatenciaMin;

    @Column(name = "seguimiento_delta_min", nullable = false)
    private double seguimientoDeltaMin;

    // --- Tiempos y Frecuencias ---
    /** Intervalo del pulso de sensado y evaluación (60–360 min, franjas ancladas a las 02:00). */
    @Column(name = "intervalo_sensado_minutos")
    private Integer intervaloSensadoMinutos = 240;

    // --- Pasada diaria del riel (pasada-programada) ---
    /** Nullable: las filas anteriores a este cambio no tienen valor y se leen como {@code false}. */
    @Column(name = "pasada_diaria_activa")
    private Boolean pasadaDiariaActiva;

    /** Hora local del vivero, {@code HH:mm}. Nullable: se lee como {@code "11:00"}. */
    @Column(name = "pasada_diaria_hora", length = 5)
    private String pasadaDiariaHora;

    /**
     * Horario por defecto de la mediasombra (hora local del vivero, {@code HH:mm}): abierta de una a otra, cerrada el
     * resto del día, en las macro-zonas sin plan de rustificación. Nullables: se leen como los de fábrica.
     */
    @Column(name = "mediasombra_abierta_desde", length = 5)
    private String mediasombraAbiertaDesde;

    @Column(name = "mediasombra_abierta_hasta", length = 5)
    private String mediasombraAbiertaHasta;

    // --- Auditoría (HU-15 CA-02) ---
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ts")
    private Long updatedTs;
}
