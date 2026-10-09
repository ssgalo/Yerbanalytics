package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code POST /api/secuencias}: {@code {"tipo":"RIEGO","parametros":{"duracionSeg":15}}}.
 * {@code tipo}: RIEGO | MEDIASOMBRA | LECTURA. {@code parametros} es opcional.
 */
public record IniciarSecuencia(String tipo, ParametrosSecuencia parametros) {
}
