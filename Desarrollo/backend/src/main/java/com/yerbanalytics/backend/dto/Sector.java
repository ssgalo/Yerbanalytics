package com.yerbanalytics.backend.dto;

/**
 * Sector del vivero. No expone métricas sensadas: la lectura pertenece a la macro-zona
 * ({@link Zona#lectura()}). Sí son propios del sector el diagnóstico de IA del plantín, los
 * actuadores y el estado de salud derivado de la lectura de su zona.
 */
public record Sector(
        String id, String zona, String zonaName, Integer n,
        String status, String color, String statusLabel, String tip,
        Diagnosis diagnosis, String reason, Actuadores actuadores
) {}
