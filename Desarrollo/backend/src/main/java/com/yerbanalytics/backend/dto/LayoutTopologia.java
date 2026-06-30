package com.yerbanalytics.backend.dto;

/**
 * Disposición visual de la topología incluida en el snapshot {@code GET /api/nursery}
 * (HU-18 CA-01): cuántas macro-zonas por fila en el panel general y cuántos sectores por fila
 * dentro de cada macro-zona. Espejo del campo {@code layout} de {@code NurseryData} del frontend.
 */
public record LayoutTopologia(
        int macroZonasPorFila,
        int sectoresPorFila
) {}
