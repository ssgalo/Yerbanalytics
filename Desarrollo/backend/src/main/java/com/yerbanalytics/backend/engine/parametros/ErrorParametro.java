package com.yerbanalytics.backend.engine.parametros;

/**
 * Error de validación asociado a la copia {@code (regla, clave)} de un parámetro; es lo que el dashboard muestra junto
 * al campo.
 */
public record ErrorParametro(String regla, String clave, String mensaje) {}
