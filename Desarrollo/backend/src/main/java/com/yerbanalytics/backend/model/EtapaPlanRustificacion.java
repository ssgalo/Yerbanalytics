package com.yerbanalytics.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Etapa de un plan de rustificación: durante {@code dias} días seguidos la mediasombra está abierta de
 * {@code abiertaDesde} a {@code abiertaHasta} ({@code HH:mm}, hora local del vivero) y cerrada el resto del día.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtapaPlanRustificacion {

    /** 1, 2, 3… en el orden en que se cumplen. */
    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "dias", nullable = false)
    private int dias;

    @Column(name = "abierta_desde", nullable = false, length = 5)
    private String abiertaDesde;

    @Column(name = "abierta_hasta", nullable = false, length = 5)
    private String abiertaHasta;
}
