package com.yerbanalytics.backend.dto;

/**
 * Tarjeta de la vista de Diagnósticos de IA.
 *
 * <p>{@code thumb} es un gradiente CSS de respaldo, no una imagen. {@code imagenUrl} apunta a
 * la fotografía real cuando el diagnóstico proviene de una captura; viene {@code null} en los
 * diagnósticos que se derivan del estado del sector, y entonces la vista usa el gradiente.
 */
public record DiagnosisCard(
        String id, String sectorId, String zonaName, String estado, Double conf,
        String sev, String sevSoft, String sevInk, String thumb, String time, boolean concluyente,
        String imagenUrl
) {}
