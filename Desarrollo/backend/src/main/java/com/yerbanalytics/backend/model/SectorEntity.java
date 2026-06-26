package com.yerbanalytics.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @Column(name = "last_reading_time")
    private Long lastReadingTime;

    @Column(name = "hum_sus_raw")
    private Double humSusRaw;

    @Column(name = "hum_amb_raw")
    private Double humAmbRaw;

    @Column(name = "temp_raw")
    private Double tempRaw;

    @Column(name = "ce_raw")
    private Double ceRaw;

    @Column(name = "uv_raw")
    private Double uvRaw;
}
