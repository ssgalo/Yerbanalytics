package com.yerbanalytics.backend.dto;

/**
 * Resumen de la topología cargada en el vivero (HU-18 CA-01). {@code generada} indica si
 * hay una grilla disponible para el mapa de producción. Espejo del tipo
 * {@code TopologiaVivero} del frontend.
 */
public record TopologiaVivero(
        int macroZonas,
        int sectoresPorMacroZona,
        int totalSectores,
        boolean generada
) {}
