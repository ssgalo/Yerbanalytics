package com.yerbanalytics.backend.dto;

import java.util.Map;

/**
 * La lectura que devolvió una secuencia de tipo LECTURA. {@code metricas} trae siempre las 10 claves
 * del contrato (con {@code null} si el nodo no midió esa), {@code ce} ya en dS/m y {@code uv} como
 * % de luz. {@code recibidaEn} es la hora de recepción del backend, en ms epoch.
 */
public record LecturaSecuencia(long recibidaEn, Map<String, Double> metricas) {
}
