package com.yerbanalytics.backend.dto;

/**
 * Disposición visual de la topología (HU-18 CA-01): cuántas macro-zonas se muestran por fila y
 * cuántos sectores por fila dentro de cada macro-zona. Solo presentación; actualizarla no
 * regenera la grilla. Espejo del tipo {@code DisposicionTopologia} del frontend.
 */
public record DisposicionTopologia(
        int macroZonasPorFila,
        int sectoresPorFila
) {}
