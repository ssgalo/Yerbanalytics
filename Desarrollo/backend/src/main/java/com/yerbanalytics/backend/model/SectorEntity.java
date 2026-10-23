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
 *
 * <p><b>{@code @DynamicUpdate}:</b> la telemetría carga los sectores y los guarda; sin esto reescribiría todas las
 * columnas, y un cambio confirmado en el medio por un UPDATE dirigido ({@code cambiarBomba}) volvería atrás.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate   // sólo escribe las columnas que cambiaron: ver SupplyDoseService (cierre de la bomba)
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

    /** Nivel de confianza del diagnóstico vigente (Baja, Media, Alta); {@code null} si no aplica o es anterior. */
    @Column(name = "diagnosis_confidence")
    private String diagnosisConfidence;

    /**
     * Epoch ms de llegada del diagnóstico vigente ({@code creadoEn} del alta). Lo escribe sólo el alta de
     * diagnósticos; {@code null} si el sector nunca recibió uno.
     */
    @Column(name = "diagnosis_ts")
    private Long diagnosisTs;

    /**
     * {@link #diagnosisTs} del último diagnóstico que usó una regla del motor. Si es menor que
     * {@code diagnosisTs} (o nulo), el diagnóstico vigente es nuevo.
     */
    @Column(name = "diagnosis_usado_ts")
    private Long diagnosisUsadoTs;

    @Column(name = "actuador_valve", nullable = false)
    private String actuadorValve;

    @Column(name = "actuador_pump", nullable = false)
    private String actuadorPump;

    @Column(name = "actuador_shade", nullable = false)
    private Integer actuadorShade;

    /**
     * Epoch ms hasta el que el sector tiene el reingreso restringido tras un fitosanitario (reglas_v2 §8.3).
     * {@code null} si nunca se le aplicó uno; un valor pasado equivale a "sin restricción". Sólo informa:
     * no bloquea actuadores.
     */
    @Column(name = "reingreso_hasta")
    private Long reentryUntil;

    // Las lecturas sensadas viven en ZonaEntity: hay un solo nodo testigo por macro-zona
    // y los 100 sectores comparten esa lectura. El sector conserva sólo lo que es suyo
    // (diagnóstico del plantín, actuadores y estado derivado).
}
