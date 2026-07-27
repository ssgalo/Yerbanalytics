package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Sector del vivero (~100 tubetes con su microaspersor). NO almacena métricas sensadas:
 * la lectura es de la macro-zona ({@link ZonaEntity}). Su estado de salud se deriva de esa
 * lectura, pero el diagnóstico de IA y los actuadores sí son propios del sector.
 */
@Entity
@Table(name = "sector")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectorEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = false)
    private ZonaEntity zona;

    @Column(nullable = false)
    private Integer n;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String color;

    @Column(name = "status_label", nullable = false)
    private String statusLabel;

    @Column(nullable = false)
    private String tip;

    @Column(nullable = false)
    private String reason;

    @Column(name = "diagnosis_estado", nullable = false)
    private String diagnosisEstado;

    @Column(name = "diagnosis_conf")
    private Double diagnosisConf;

    @Column(name = "diagnosis_sev", nullable = false)
    private String diagnosisSev;

    @Column(name = "actuador_valve", nullable = false)
    private String actuadorValve;

    @Column(name = "actuador_pump", nullable = false)
    private String actuadorPump;

    @Column(name = "actuador_shade", nullable = false)
    private Integer actuadorShade;

    // Las lecturas sensadas viven en ZonaEntity: hay un solo nodo testigo por macro-zona
    // y los 100 sectores comparten esa lectura. El sector conserva sólo lo que es suyo
    // (diagnóstico del plantín, actuadores y estado derivado).
}
