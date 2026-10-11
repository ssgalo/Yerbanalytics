package com.yerbanalytics.backend.dto;

/**
 * Parámetros de una secuencia, de entrada y de salida. {@code duracionSeg} es del riego (segundos con
 * la válvula abierta) y {@code esperaSeg} de la mediasombra (segundos desplegada); el que no aplica
 * viaja {@code null}. La lectura no lleva parámetros.
 */
public record ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg) {
}
