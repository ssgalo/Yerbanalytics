package com.yerbanalytics.backend.dto;

/**
 * Sector del vivero. No expone métricas sensadas: la lectura pertenece a la macro-zona
 * ({@link Zona#lectura()}). Sí son propios del sector el diagnóstico de IA del plantín, los
 * actuadores y el estado de salud derivado de la lectura de su zona.
 *
 * <p>{@code reentryUntil} es el epoch ms hasta el que el sector tiene el reingreso restringido tras un fitosanitario
 * (reglas_v2 §8.3); {@code null} si nunca se le aplicó uno. Pasado ese instante ya no restringe.
 */
public record Sector(
        String id, String zona, String zonaName, Integer n,
        String status, String color, String statusLabel, String tip,
        Diagnosis diagnosis, String reason, Actuadores actuadores, Long reentryUntil
) {}
