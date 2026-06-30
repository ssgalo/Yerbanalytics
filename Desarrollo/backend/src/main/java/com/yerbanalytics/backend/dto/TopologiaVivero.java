package com.yerbanalytics.backend.dto;

/**
 * Resumen de la topología cargada en el vivero (HU-18 CA-01). {@code generada} indica si
 * hay una grilla disponible para el mapa de producción; {@code macroZonasPorFila} y
 * {@code sectoresPorFila} son la disposición visual configurada. Espejo del tipo
 * {@code TopologiaVivero} del frontend.
 */
public record TopologiaVivero(
        int macroZonas,
        int sectoresPorMacroZona,
        int totalSectores,
        boolean generada,
        int macroZonasPorFila,
        int sectoresPorFila
) {}
