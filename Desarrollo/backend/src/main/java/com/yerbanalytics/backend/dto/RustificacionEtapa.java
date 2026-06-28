package com.yerbanalytics.backend.dto;

/** Etapa del plan de rustificación (HU-15 CA-06). */
public record RustificacionEtapa(
        int orden,
        int diaDesde,
        int diaHasta,
        int aperturaPct
) {}
