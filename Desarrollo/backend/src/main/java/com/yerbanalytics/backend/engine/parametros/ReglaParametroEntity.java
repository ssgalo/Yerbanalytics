package com.yerbanalytics.backend.engine.parametros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Override de la copia de un parámetro que tiene una pieza del motor (rediseno-motor-reglas D-1). Sólo se guardan los
 * valores que se apartan de fábrica: restablecer es borrar la fila. No hay herencia entre piezas: una copia sin fila está
 * en fábrica aunque otra pieza haya editado la misma clave. Las definiciones (tipo, rango, unidad, fábrica) viven en
 * código ({@link CatalogoParametros}) y no se duplican acá.
 */
@Entity
@Table(name = "regla_parametro")
@IdClass(ReglaParametroId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReglaParametroEntity {

    /** {@link ConsumidorParametros#name()} de la pieza dueña de la copia. */
    @Id
    @Column(name = "consumidor")
    private String consumidor;

    /** Clave del catálogo, p. ej. {@code riego.umbral-humedad}. */
    @Id
    @Column(name = "clave")
    private String clave;

    /** Valor en formato canónico por tipo ({@code "42"}, {@code "0.2"}, {@code "06:00-18:00"}). */
    @Column(name = "valor", nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ts")
    private Long updatedTs;
}
