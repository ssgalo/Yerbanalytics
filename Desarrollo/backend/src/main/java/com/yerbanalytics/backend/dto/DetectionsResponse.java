package com.yerbanalytics.backend.dto;

import java.util.List;

/** Respuesta de {@code GET /api/diagnosticos/{id}/detecciones}. */
public record DetectionsResponse(List<Detection> detections) {
}
