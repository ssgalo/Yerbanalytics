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
 * Diagnóstico emitido sobre una captura (HU-04 / HU-05).
 *
 * <p><strong>No hay columna de origen.</strong> Un diagnóstico cargado a mano desde el panel
 * cargado a mano y uno emitido por el modelo de IA son la misma fila, porque entran por la
 * misma operación: {@code POST /api/diagnosticos}. Una marca que distinguiera el ensayo de la
 * operación real volvería infiel el ensayo, y quedaría para siempre en el esquema como
 * residuo de una etapa de pruebas.
 *
 * <p>Consecuencia asumida: no se pueden purgar selectivamente los diagnósticos de prueba. La
 * purga posible es por rango de fechas o por sector, que alcanza porque todo diagnóstico está
 * anclado a una captura fechada.
 *
 * <p>{@code capturaId} es obligatorio: todo diagnóstico nace del análisis de una imagen. Esa
 * restricción es lo que impide falsear el camino, incluso por accidente.
 */
@Entity
@Table(name = "diagnostico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticoEntity {

    @Id
    private String id;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    /** Captura que originó el diagnóstico. Obligatoria a nivel de esquema. */
    @Column(name = "captura_id", nullable = false)
    private String capturaId;

    /** Estado de la taxonomía de la plataforma (Sano, Clorosis, Estrés solar, …). */
    @Column(name = "estado", nullable = false)
    private String estado;

    /**
     * Score promedio del estado diagnosticado × 100 (0-100). {@code null} en No concluyente.
     *
     * <p>{@code ddl-auto=update} no quita el {@code NOT NULL} que tenía la columna: hace falta
     * {@code migracion-diagnostico-detecciones.sql}.
     */
    @Column(name = "conf")
    private Double conf;

    /** Severidad asignada (Alta, Media, Baja o em dash cuando no aplica). */
    @Column(name = "sev", nullable = false)
    private String sev;

    /**
     * Nivel de confianza del diagnóstico (Baja, Media, Alta). {@code null} en No concluyente y en
     * los diagnósticos anteriores a este campo, que la vista muestra "sin nivel".
     */
    @Column(name = "confidence")
    private String confidence;

    /**
     * Detecciones que intervinieron, como JSON ({@code [{"label", "score", "box"}]}). Se leen
     * siempre juntas para dibujarlas, nunca por campo: por eso no son una tabla (design D8).
     */
    @Column(name = "detections", columnDefinition = "text")
    private String detections;

    /**
     * Cantidad de detecciones, para que el snapshot no tenga que parsear el JSON. Nullable a
     * propósito: {@code ddl-auto} la agrega a una tabla con filas, y las anteriores quedan en
     * {@code null}, que se lee como 0 ({@link #getDetectionCount()}).
     */
    @Column(name = "detection_count")
    private Integer detectionCount;

    @Column(name = "creado_en", nullable = false)
    private Long creadoEn;

    /** Cantidad de detecciones; las filas anteriores a la columna cuentan 0. */
    public int getDetectionCount() {
        return detectionCount != null ? detectionCount : 0;
    }
}
