package com.yerbanalytics.backend.dto;

/**
 * Bandas configurables de una métrica (HU-15). Incluye los metadatos derivados de
 * {@code NurseryConstants.SPECS} (label, unit, dec) para que la UI los muestre sin
 * duplicar la fuente de verdad.
 */
public record UmbralMetrica(
        String key,
        String label,
        String unit,
        Integer dec,
        double idealMin,
        double idealMax,
        double warnMin,
        double warnMax,
        double critMin,
        double critMax
) {}
