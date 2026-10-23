package com.yerbanalytics.backend.engine.overrides;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Decisión operativa de prender o apagar una pieza del motor: una fila por pieza, con el último cambio (quién, cuándo,
 * por qué). Una pieza sin fila toma su estado por defecto (ver {@link OperationalRuleStates}), así que la fila existe
 * sólo si alguien la cambió.
 */
@Entity
@Table(name = "regla_estado")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RuleStateEntity {

    /** {@code name()} del {@link DemoControllable}. */
    @Id
    @Column(name = "nombre")
    private String nombre;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    /** Por qué se cambió; obligatorio sólo al apagar una protección. */
    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ts")
    private Long updatedTs;
}
