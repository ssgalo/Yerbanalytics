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
 * Disposición visual de la topología (HU-18 CA-01): cuántas macro-zonas se muestran por fila
 * en el panel general y cuántos sectores por fila dentro de cada macro-zona. Es presentación,
 * no afecta la grilla lógica de zonas/sectores. Fila única ({@code id = 1}); la disposición
 * es global. Espejo del tipo de disposición del frontend.
 */
@Entity
@Table(name = "topologia_layout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopologiaLayoutEntity {

    @Id
    private Integer id;

    @Column(name = "macro_zonas_por_fila", nullable = false)
    private int macroZonasPorFila;

    @Column(name = "sectores_por_fila", nullable = false)
    private int sectoresPorFila;
}
