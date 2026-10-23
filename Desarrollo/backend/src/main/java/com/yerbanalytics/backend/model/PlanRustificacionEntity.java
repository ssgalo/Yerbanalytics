package com.yerbanalytics.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan de rustificación de una macro-zona (add-rustificacion-por-macrozona): desde {@code fechaInicio}, etapas de N días
 * con una ventana diaria en que la mediasombra está abierta. Mientras rige reemplaza el horario por defecto de la
 * mediasombra en esa zona.
 *
 * <p>El estado (programado / en curso / cancelado) no se guarda: sale de las fechas y de {@code canceladoEn}. Pasada la
 * última etapa sigue rigiendo la última, hasta que alguien elimina el plan. Una zona tiene a lo sumo un plan vigente
 * ({@code canceladoEn == null}); los eliminados quedan como registro.
 *
 * <p>Las tablas las crea Hibernate ({@code ddl-auto=update}).
 */
@Entity
@Table(name = "plan_rustificacion")
@Getter
@Setter
@NoArgsConstructor
public class PlanRustificacionEntity {

    @Id
    private String id;

    @Column(name = "zona_id", nullable = false)
    private String zonaId;

    /** Primer día del plan (fecha local del vivero). */
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "etapa_plan_rustificacion", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderBy("orden ASC")
    private List<EtapaPlanRustificacion> etapas = new ArrayList<>();

    @Column(name = "creado_por")
    private String creadoPor;

    /** Epoch ms. */
    @Column(name = "creado_en", nullable = false)
    private Long creadoEn;

    /** Epoch ms en que se eliminó; {@code null} mientras rige. */
    @Column(name = "cancelado_en")
    private Long canceladoEn;
}
