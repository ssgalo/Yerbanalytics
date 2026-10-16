package com.yerbanalytics.backend.dto;

/**
 * Diagnóstico de la foto de un paso de la pasada; mismos valores que "Diagnósticos de IA".
 * {@code conf} es {@code null} en No concluyente.
 */
public record DiagnosticoPaso(String estado, Double conf, String sev, String confidence, long creadoEn) {}
