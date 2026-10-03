package com.yerbanalytics.backend.engine.parametros;

/**
 * Definición de un parámetro de regla: todo lo que no cambia en runtime. Cada familia la
 * implementa con un enum (ver {@code ParametrosRiego}, {@code ParametrosSeguridad}…), así el
 * valor de fábrica vive en código y no depende de ningún seed de base de datos.
 *
 * <p>El valor vigente (fábrica o override) NO está acá: lo resuelve {@link CatalogoParametrosService}.
 */
public interface DefinicionParametro {

    /** Clave estable en kebab-case; el prefijo es la familia (p. ej. {@code riego.umbral-humedad}). */
    String clave();

    String etiqueta();

    String descripcion();

    FamiliaParametro familia();

    TipoParametro tipo();

    /** Unidad para mostrar ({@code %}, {@code °C}, {@code L}, {@code s}…); vacía si no aplica. */
    String unidad();

    /** Valor de fábrica en formato canónico ({@code "42"}, {@code "06:00-18:00"}). */
    String fabrica();

    /** Mínimo permitido, o {@code null} si no tiene (horas y ventanas nunca lo tienen). */
    Double min();

    /** Máximo permitido, o {@code null} si no tiene. */
    Double max();

    /** Cantidad máxima de decimales; 0 para enteros. */
    int decimales();

    /** Referencia a la especificación agronómica (p. ej. {@code reglas_v2 §11 Riego}). */
    String refSpec();
}
