package com.yerbanalytics.backend.dto;

/** Diagnóstico de la foto de un paso de la pasada; mismos valores que "Diagnósticos de IA". */
public record DiagnosticoPaso(String estado, double conf, String sev, long creadoEn) {}
