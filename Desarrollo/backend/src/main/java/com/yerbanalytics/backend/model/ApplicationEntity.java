package com.yerbanalytics.backend.model;

import com.yerbanalytics.backend.supply.Supply;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una aplicación de insumo que el sistema ordenó: sector, insumo, volumen, hora y regla de origen (reglas_v2 §8.1).
 * Es lo que lee P-D2 para el intervalo mínimo y la regla de "una por día". La fila del historial ("Insumo") la escribe
 * el mismo servicio aparte: R-06 y el seguimiento de efectividad siguen leyendo de ahí.
 */
@Entity
@Table(name = "application", indexes = @Index(name = "idx_application_sector_ts", columnList = "sector_id, ts"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationEntity {

    @Id
    private String id;

    @Column(name = "sector_id", nullable = false)
    private String sectorId;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Supply supply;

    /** Mililitros que se le ordenaron al nodo. */
    @Column(nullable = false)
    private Integer ml;

    /** Epoch ms de la orden (reloj del vivero). */
    @Column(nullable = false)
    private Long ts;

    /** Regla que decidió aplicar. */
    @Column(name = "rule_name", nullable = false)
    private String ruleName;
}
