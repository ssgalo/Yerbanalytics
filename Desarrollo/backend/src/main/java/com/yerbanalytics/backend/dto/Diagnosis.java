package com.yerbanalytics.backend.dto;

/**
 * Diagnóstico vigente de un sector en el snapshot.
 *
 * <p>{@code conf} es el score promedio × 100 del estado diagnosticado ({@code null} en No
 * concluyente); {@code confidence}, su nivel de confianza (Baja, Media, Alta), o {@code null}
 * cuando no aplica o el diagnóstico es anterior a ese campo.
 */
public record Diagnosis(String estado, Double conf, String sev, String confidence) {
}
