package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de historial inmutable (solo insert). Registra una acción ejecutada por el
 * sistema con su cadena de justificación y el seguimiento post-acción. No se expone
 * ninguna operación de edición/borrado: la inalterabilidad es por construcción.
 */
@Entity
@Table(name = "historial_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEventoEntity {

    @Id
    private String id;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    @Column(name = "zona_name", nullable = false)
    private String zonaName;

    /** Riego | Insumo | Mediasombra */
    @Column(nullable = false)
    private String tipo;

    /** Marca temporal en epoch ms. */
    @Column(nullable = false)
    private Long ts;

    // --- Cadena de justificación (lectura/diagnóstico → decisión → acción) ---
    @Column(columnDefinition = "TEXT", nullable = false)
    private String lectura;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String decision;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String accion;

    /** Resultado de ejecución: Efectiva | En seguimiento | Pospuesta | Abortada */
    @Column(nullable = false)
    private String res;

    @Column(nullable = false)
    private String sev;

    // --- Datos internos para evaluar el seguimiento ---
    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "valor_antes")
    private Double valorAntes;

    @Column(name = "umbral_recuperacion")
    private Double umbralRecuperacion;

    @Column(name = "latency_ms")
    private Long latencyMs;

    // --- Seguimiento post-acción (HU-12) ---
    @Column(name = "evo_show", nullable = false)
    private boolean evoShow;

    @Column(name = "evo_metric")
    private String evoMetric;

    @Column(name = "evo_antes")
    private String evoAntes;

    @Column(name = "evo_ahora")
    private String evoAhora;

    @Column(name = "evo_unit")
    private String evoUnit;

    @Column(name = "evo_delta")
    private String evoDelta;

    @Column(name = "evo_latencia")
    private String evoLatencia;

    /** En seguimiento | Efectiva | Sin efectividad */
    @Column(name = "evo_verdict")
    private String evoVerdict;

    /** Epoch ms de la evaluación; null mientras la latencia no venció. */
    @Column(name = "evo_evaluado_ts")
    private Long evoEvaluadoTs;

    /** Bloqueo de repetición autónoma ante "Sin efectividad" (HU-12 CA-03). */
    @Column(name = "bloqueo_repeticion", nullable = false)
    private boolean bloqueoRepeticion;
}
