package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code POST /api/sectores/{sectorId}/revision-fitosanitario}: qué se revisó en el sector.
 *
 * @param detail lo que hizo o constató quien revisó; obligatorio
 */
public record NewFungicideReview(String detail) {
}
