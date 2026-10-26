package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/diagnosticos/{id}/detecciones}.
 *
 * <p>{@code crop} es la región analizada ({@code [x1, y1, x2, y2]} normalizado sobre la imagen
 * orientada), o {@code null} si el diagnóstico no tiene recorte: entonces las cajas son relativas a
 * la imagen entera.
 */
public record DetectionsResponse(List<Detection> detections, List<Double> crop) {
}
