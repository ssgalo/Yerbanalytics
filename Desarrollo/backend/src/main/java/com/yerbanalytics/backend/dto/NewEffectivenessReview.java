package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code POST /api/efectividad/{eventId}/revision}: qué se revisó en el vivero.
 *
 * @param detail lo que hizo o constató quien revisó; obligatorio
 */
public record NewEffectivenessReview(String detail) {
}
