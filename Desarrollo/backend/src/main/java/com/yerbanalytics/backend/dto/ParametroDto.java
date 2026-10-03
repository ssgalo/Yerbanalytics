package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Un parámetro del catálogo con su valor vigente. Los valores viajan como texto canónico
 * ({@code "42"}, {@code "06:00-18:00"}): el tipo dice cómo interpretarlos.
 *
 * @param familia   familia de la clave ({@code RIEGO}, {@code SEGURIDAD}…)
 * @param tipo      {@code NUMERO | ENTERO | HORA | VENTANA_HORARIA}
 * @param valor     valor vigente (override o, si no hay, fábrica)
 * @param modificado {@code true} si hay un override persistido
 * @param usadoPor  ids de las reglas que declaran el parámetro, por prioridad
 * @param updatedBy / updatedTs auditoría del override; {@code null} si está en fábrica
 */
public record ParametroDto(
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
        List<String> usadoPor,
        String updatedBy,
        Long updatedTs
) {}
