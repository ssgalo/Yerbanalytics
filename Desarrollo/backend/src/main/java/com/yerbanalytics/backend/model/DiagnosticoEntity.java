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
 * de simulación y uno emitido por el modelo de IA son la misma fila, porque entran por la
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

    /** Confianza del modelo, 0-100. */
    @Column(name = "conf", nullable = false)
    private Double conf;

    /** Severidad asignada (Alta, Media, Baja o em dash). */
    @Column(name = "sev", nullable = false)
    private String sev;

    @Column(name = "creado_en", nullable = false)
    private Long creadoEn;
}
