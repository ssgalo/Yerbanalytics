package com.yerbanalytics.backend.engine.parametros;

/** Error de validación asociado a un parámetro; es lo que el dashboard muestra junto al campo. */
public record ErrorParametro(String clave, String mensaje) {}
