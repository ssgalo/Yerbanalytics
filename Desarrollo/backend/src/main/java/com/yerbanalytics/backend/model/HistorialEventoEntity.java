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
@Table(name = "historial_evento", indexes = {
        // El contexto de riego (cada mensaje del nodo): WHERE zona_id = ? AND tipo IN ('Riego','Insumo') AND ts >= ?
        // → igualdad, lista y rango, en ese orden. Mismo nombre que migracion-reglas-riego.sql.
        @Index(name = "idx_historial_evento_zona_tipo_ts", columnList = "zona_id, tipo, ts"),
        // Riegos abiertos tras un reinicio (tipo = 'Riego' AND ts >= ?) y KPI por tipo y día.
        @Index(name = "idx_historial_evento_tipo_ts", columnList = "tipo, ts"),
        // S-06: ¿hay una acción de este tipo bloqueando a la zona o al sector? Se pregunta en cada evaluación.
        @Index(name = "idx_historial_evento_bloqueo", columnList = "bloqueo_repeticion, efectividad_tipo, zona_id")
})
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

    /** Riego | Insumo | Mediasombra | Info | Configuración | Alerta */
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

    /**
     * Bloqueo de la repetición autónoma ante "Sin efectividad" (S-06): sigue en pie hasta que alguien registre la revisión, que
     * lo baja. Sólo bloquea un evento con {@link #efectividadTipo}: los de antes de la evaluación v2 no cuentan.
     */
    @Column(name = "bloqueo_repeticion", nullable = false)
    private boolean bloqueoRepeticion;

    // --- Efectividad v2 (E-01…E-03): qué se evalúa y contra qué se compara (nulos en los eventos sin seguimiento) ---

    /** {@code EffectivenessKind}: IRRIGATION | CORRECTIVE_FERTILIZER | FUNGICIDE. Nulo si el evento no se evalúa. */
    @Column(name = "efectividad_tipo")
    private String efectividadTipo;

    /** Estado del diagnóstico del sector cuando se aplicó el tratamiento (E-02, E-03); su severidad es {@link #sev}. */
    @Column(name = "diag_estado_antes")
    private String diagEstadoAntes;

    /** Lindantes (ids separados por coma) que ya tenían ese diagnóstico al aplicar; los que aparezcan después son nuevos (E-03). */
    @Column(name = "vecinos_antes", columnDefinition = "TEXT")
    private String vecinosAntes;

    /** Por qué la evaluación dio efectiva o sin efectividad, para quien registra la revisión. */
    @Column(name = "evo_motivo", columnDefinition = "TEXT")
    private String evoMotivo;

    // --- Riego y alertas del motor (nulos en los eventos que no los usan) ---

    /** Regla que ordenó la acción (p. ej. {@code RiegoPorDeficitRule}). */
    @Column(name = "regla")
    private String regla;

    /** Nivel de una alerta: INFO | WARNING | CRITICAL. Nulo si el evento no es una alerta. */
    @Column(name = "alerta")
    private String alerta;

    /** Volumen de riego ordenado, en litros. */
    @Column(name = "volumen_l")
    private Double volumenL;

    /** Duración de apertura de la válvula ordenada, en segundos. */
    @Column(name = "duracion_seg")
    private Integer duracionSeg;

    /**
     * Piezas apagadas (estado efectivo) en el momento en que el motor registró esta decisión: nombres separados por
     * coma, ordenados. Vacío si estaba todo prendido; {@code null} en un evento anterior a este registro o que no es una
     * decisión del motor. Con esto el grafo del historial dibuja lo que corrió entonces, no lo que está prendido hoy.
     */
    @Column(name = "reglas_inactivas", columnDefinition = "TEXT")
    private String reglasInactivas;
}
