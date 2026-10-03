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
        Evolution evo,
        /** Regla que ordenó la acción; nulo si no aplica. */
        String regla,
        /** Nivel de la alerta (INFO | WARNING | CRITICAL); nulo si el evento no es una alerta. */
        String alerta,
        /** Volumen de riego ordenado, en litros; nulo si no es un riego. */
        Double volumenL,
        /** Duración de apertura ordenada, en segundos; nulo si no es un riego. */
        Integer duracionSeg
) {}
