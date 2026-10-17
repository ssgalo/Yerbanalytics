package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code POST /api/hardware/{id}/reparacion} (S-05): qué se hizo para arreglar el dispositivo. Es obligatorio
 * y queda en el historial junto al usuario de la sesión. Espejo del tipo {@code RepairRequest} del frontend.
 */
public record RepairRequest(String detalle) {
}
