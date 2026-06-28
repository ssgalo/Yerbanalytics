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
 * Etapa del plan de rustificación (HU-15 CA-06): un tramo de días con su porcentaje
 * de apertura de mediasombra. El plan es la lista ordenada de etapas.
 */
@Entity
@Table(name = "rustificacion_etapa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RustificacionEtapaEntity {

    @Id
    private Integer orden;

    @Column(name = "dia_desde", nullable = false)
    private int diaDesde;

    @Column(name = "dia_hasta", nullable = false)
    private int diaHasta;

    @Column(name = "apertura_pct", nullable = false)
    private int aperturaPct;
}
