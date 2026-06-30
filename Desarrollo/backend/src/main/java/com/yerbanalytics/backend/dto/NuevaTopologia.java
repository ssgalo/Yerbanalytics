package com.yerbanalytics.backend.dto;

/**
 * Solicitud de generación de la topología del vivero (HU-18 CA-01): cantidad de
 * macro-zonas y de sectores por macro-zona, más la disposición visual por fila
 * ({@code macroZonasPorFila}, {@code sectoresPorFila}) que se guarda junto con la grilla.
 * {@code regenerar} habilita reemplazar una topología ya cargada; sin él, el alta sobre un
 * vivero con topología responde 409.
 */
public record NuevaTopologia(
        int macroZonas,
        int sectoresPorMacroZona,
        boolean regenerar,
        int macroZonasPorFila,
        int sectoresPorFila
) {}
