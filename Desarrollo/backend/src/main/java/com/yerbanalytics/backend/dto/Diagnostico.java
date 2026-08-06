package com.yerbanalytics.backend.dto;

/**
 * Diagnostico persistido. SIN campo de origen: un diagnostico cargado a mano y uno emitido
 * por el modelo son la misma fila, porque entran por la misma operacion.
 */
public record Diagnostico(
        String id,
        String sectorId,
        String zonaId,
        String capturaId,
        String imagenUrl,
        String estado,
        double conf,
        String sev,
        boolean concluyente,
        long creadoEn
) {}
