package com.yerbanalytics.backend.dto;

/**
 * Diagnostico persistido. SIN campo de origen: un diagnostico cargado a mano y uno emitido
 * por el modelo son la misma fila, porque entran por la misma operacion.
 *
 * <p>{@code conf} es el score promedio × 100 ({@code null} en No concluyente), {@code confidence}
 * el nivel de confianza y {@code detectionCount} la cantidad de detecciones (las cajas se piden
 * aparte). {@code concluyente} = el estado no es No concluyente.
 */
public record Diagnostico(
        String id,
        String sectorId,
        String zonaId,
        String capturaId,
        String imagenUrl,
        String estado,
        Double conf,
        String sev,
        boolean concluyente,
        long creadoEn,
        String confidence,
        int detectionCount
) {}
