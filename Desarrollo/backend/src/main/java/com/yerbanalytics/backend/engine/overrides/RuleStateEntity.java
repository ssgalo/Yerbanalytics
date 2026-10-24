package com.yerbanalytics.backend.engine.overrides;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Decisión operativa sobre una pieza del motor: una fila por pieza, con el último cambio del interruptor (quién, cuándo,
 * por qué), su nombre propio y si se eliminó de la vista. Una pieza sin fila toma su estado por defecto (ver
 * {@link OperationalRuleStates}), así que la fila existe sólo si alguien la cambió.
 *
 * <p>Una fila creada sólo para renombrar no decide el interruptor: queda con {@code updated_ts} nulo y rige la siembra
 * (ver {@link RuleState#switchDecided()}).
 */
@Entity
@Table(name = "regla_estado")
@Getter
@NoArgsConstructor
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

    /** Cuándo se cambió el interruptor; {@code null} si la fila existe sólo por el nombre propio. */
    @Column(name = "updated_ts")
    private Long updatedTs;

    /** Nombre propio que le dio el vivero; {@code null} = el de fábrica (rediseno-motor-reglas D-5). */
    @Column(name = "etiqueta", columnDefinition = "TEXT")
    private String etiqueta;

    /** {@code true} si se sacó de la vista (y está apagada); {@code null} o {@code false} = no eliminada. */
    @Column(name = "eliminada")
    private Boolean eliminada;

    public RuleStateEntity(String nombre, boolean activa, String motivo, String updatedBy, Long updatedTs) {
        this(nombre, activa, motivo, updatedBy, updatedTs, null, null);
    }

    public RuleStateEntity(String nombre, boolean activa, String motivo, String updatedBy, Long updatedTs,
                           String etiqueta, Boolean eliminada) {
        this.nombre = nombre;
        this.activa = activa;
        this.motivo = motivo;
        this.updatedBy = updatedBy;
        this.updatedTs = updatedTs;
        this.etiqueta = etiqueta;
        this.eliminada = eliminada;
    }
}
