package com.yerbanalytics.backend.engine.parametros;

/**
 * Un cambio del lote de {@code PUT /api/rules/parametros}.
 *
 * @param valor texto del valor nuevo; {@code null} restablece el valor de fábrica
 */
public record CambioParametro(String clave, String valor) {}
