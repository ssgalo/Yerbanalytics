package com.yerbanalytics.backend.engine.parametros;

/**
 * Un cambio del lote de {@code PUT /api/rules/parametros}: el valor de la copia {@code (regla, clave)}.
 *
 * @param regla nombre técnico de la pieza dueña de la copia ({@link ConsumidorParametros#name()})
 * @param valor texto del valor nuevo; {@code null} restablece el valor de fábrica
 */
public record CambioParametro(String regla, String clave, String valor) {}
