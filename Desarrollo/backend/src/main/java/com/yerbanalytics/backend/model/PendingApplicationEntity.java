package com.yerbanalytics.backend.model;

import com.yerbanalytics.backend.supply.Supply;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una aplicación que no cumplió las precondiciones y espera la próxima ventana apta (reglas_v2: "se vuelve a evaluar en
 * la próxima ventana"). A lo sumo una por sector e insumo: si la regla vuelve a pedir lo mismo, se actualiza la que hay.
 * Sale de la cola cuando se aplica, cuando la regla de origen la cancela, cuando deja de ser válida o cuando vence.
 */
@Entity
@Table(name = "pending_application",
        uniqueConstraints = @UniqueConstraint(name = "uk_pending_sector_supply", columnNames = {"sector_id", "supply"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingApplicationEntity {

    @Id
    private String id;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Supply supply;

    /** Regla que pidió la aplicación. */
    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    /** Si la dispara un diagnóstico: hay que volver a verificar P-D1 al reintentar. */
    @Column(name = "requires_diagnosis", nullable = false)
    private boolean requiresDiagnosis;

    /** Epoch ms del primer pedido: de acá corre el vencimiento. */
    @Column(name = "created_ts", nullable = false)
    private Long createdTs;

    /** Por qué se difirió la última vez que se evaluó. */
    @Column(name = "last_reason", columnDefinition = "TEXT")
    private String lastReason;

    /**
     * {@code diagnosisTs} del diagnóstico que originó el pedido, si lo disparó uno. El diagnóstico se da por usado cuando la
     * aplicación se ejecuta, no cuando se pide; y se usa ESTE, no el que tenga el sector cuando por fin se aplique.
     */
    @Column(name = "diagnosis_ts")
    private Long diagnosisTs;

    /** Sin diagnóstico de origen: el pedido de una regla que no decide con la IA. */
    public PendingApplicationEntity(String id, String sectorId, String zoneId, Supply supply, String ruleName,
                                    boolean requiresDiagnosis, Long createdTs, String lastReason) {
        this(id, sectorId, zoneId, supply, ruleName, requiresDiagnosis, createdTs, lastReason, null);
    }
}
