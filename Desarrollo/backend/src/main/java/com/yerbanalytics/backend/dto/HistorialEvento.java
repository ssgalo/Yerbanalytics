package com.yerbanalytics.backend.dto;

/**
 * Evento de historial para el frontend (espejo del tipo {@code ActionRecord}).
 * Incluye la cadena de justificación, los colores derivados de la paleta de dominio
 * y, si corresponde, el seguimiento post-acción.
 */
public record HistorialEvento(
        String id,
        String sectorId,
        String zonaName,
        String tipo,
        String time,
        long ts,
        String fecha,
        String lectura,
        String decision,
        String accion,
        String res,
        String resSoft,
        String resInk,
        String sev,
        String tint,
        String ink,
        String path,
        Evolution evo
) {}
