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
 * Criterio agronómico del diagnóstico para un estado anómalo que emite el modelo (design D5):
 * bandas de severidad en cantidad de hojas por sector y su puesto en el orden de criticidad.
 *
 * <p>Una fila por estado (Clorosis, Estrés solar, Plaga foliar, Daño fúngico). El backend sólo lo
 * guarda, lo valida y lo sirve: quien lo aplica es el módulo de diagnóstico del servicio de
 * inferencia, que lo lee en cada ciclo.
 */
@Entity
@Table(name = "diagnosis_criteria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisCriteriaEntity {

    /** Estado de la plataforma. */
    @Id
    @Column(name = "state")
    private String state;

    /** Cantidad mínima de hojas para que el estado cuente (severidad Baja desde acá). */
    @Column(name = "min_count", nullable = false)
    private Integer minCount;

    /** Desde cuántas hojas la severidad es Media. */
    @Column(name = "medium_from", nullable = false)
    private Integer mediumFrom;

    /** Desde cuántas hojas la severidad es Alta. */
    @Column(name = "high_from", nullable = false)
    private Integer highFrom;

    /** 1 = el más crítico; los cuatro estados forman una permutación de 1..4. */
    @Column(name = "criticality_rank", nullable = false)
    private Integer criticalityRank;
}
