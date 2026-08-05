package com.yerbanalytics.backend.dto;

/**
 * Respuesta a la subida de una imagen. Se devuelve tanto en el 201 (creada) como en el
 * 409 (la orden ya tenia captura): el contrato obliga al cliente a tratar el 409 como
 * exito, y para eso necesita el {@code capturaId} que ya existe.
 */
public record CapturaCreada(
        String capturaId,
        String ordenId,
        String imagenUrl
) {}
