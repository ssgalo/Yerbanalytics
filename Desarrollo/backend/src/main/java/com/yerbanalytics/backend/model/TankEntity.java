package com.yerbanalytics.backend.model;

import com.yerbanalytics.backend.supply.Supply;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tanque de un insumo en una macro-zona (un tanque por insumo y por MZ). Lo carga el operario y cada aplicación lo
 * descuenta. No existe hasta que alguien lo registra: un tanque sin registrar es un stock desconocido, no un tanque lleno.
 */
@Entity
@Table(name = "supply_tank",
        uniqueConstraints = @UniqueConstraint(name = "uk_tank_zone_supply", columnNames = {"zone_id", "supply"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TankEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Supply supply;

    @Column(name = "capacity_ml", nullable = false)
    private Long capacityMl;

    @Column(name = "level_ml", nullable = false)
    private Long levelMl;

    @Column(name = "updated_ts", nullable = false)
    private Long updatedTs;

    /** Quién hizo la última carga; {@code null} si sólo hubo descuentos. */
    @Column(name = "updated_by")
    private String updatedBy;
}
