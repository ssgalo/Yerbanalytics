package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code PUT /api/rules/{name}/etiqueta}.
 *
 * @param etiqueta nombre propio, 1–80 caracteres después de recortar; {@code null} o vacío restablece el de fábrica
 */
public record CambioEtiquetaReglaRequest(String etiqueta) {}
