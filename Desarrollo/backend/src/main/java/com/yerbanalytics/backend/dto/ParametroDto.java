package com.yerbanalytics.backend.dto;

/**
 * La copia de un parámetro que tiene una pieza, con su valor vigente (rediseno-motor-reglas D-3). Los valores viajan
 * como texto canónico ({@code "42"}, {@code "06:00-18:00"}): el tipo dice cómo interpretarlos. La definición (tipo,
 * rango, unidad, fábrica) es la de la clave y se repite en cada copia.
 *
 * @param regla      pieza dueña de la copia ({@code ConsumidorParametros.name()}; para las reglas, su {@code id})
 * @param familia    familia de la clave ({@code RIEGO}, {@code SEGURIDAD}…)
 * @param tipo       {@code NUMERO | ENTERO | HORA | VENTANA_HORARIA}
 * @param valor      valor vigente de la copia (override o, si no hay, fábrica)
 * @param modificado {@code true} si la copia tiene un override persistido
 * @param updatedBy / updatedTs auditoría del override; {@code null} si está en fábrica
 */
public record ParametroDto(
        String regla,
        String clave,
        String etiqueta,
        String descripcion,
        String familia,
        String tipo,
        String unidad,
        String valor,
        String fabrica,
        Double min,
        Double max,
        int decimales,
        String refSpec,
        boolean modificado,
        String updatedBy,
        Long updatedTs
) {}
