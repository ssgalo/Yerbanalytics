package com.yerbanalytics.backend.dto;

/**
 * La revisión registrada de una acción sin efectividad (S-06).
 *
 * @param eventId       el evento del historial revisado
 * @param kind          qué acción era ({@code IRRIGATION}, {@code CORRECTIVE_FERTILIZER}, {@code FUNGICIDE})
 * @param clearedEvents cuántos bloqueos se levantaron (el riego bloquea a toda la macro-zona: pueden ser varios)
 * @param reviewedTs    cuándo se registró (epoch ms, reloj del vivero)
 */
public record EffectivenessReview(String eventId, String kind, int clearedEvents, long reviewedTs) {
}
