package com.yerbanalytics.backend.engine.traza;

/**
 * Una comparación que decidió (o ayudó a decidir) el resultado de una regla.
 *
 * <p>{@code umbral} es el VALOR usado en esa evaluación, no una referencia al parámetro: si
 * después se cambia el parámetro, la traza vieja sigue mostrando contra qué se comparó.
 * {@code recibido} es {@code null} cuando no había dato ({@link ResultadoComparacion#SIN_DATO}).
 *
 * @param clave        clave del parámetro del catálogo; {@code null} si la condición no es configurable
 * @param configurable {@code false} para las condiciones fijas (p. ej. {@code estado == critical})
 */
public record Comparacion(
        String etiqueta,
        String clave,
        Object recibido,
        Operador operador,
        Object umbral,
        String unidad,
        boolean configurable,
        ResultadoComparacion resultado
) {}
