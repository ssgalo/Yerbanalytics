package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Regla del motor en el catálogo: referencia a sus parámetros por clave, que están una sola
 * vez en {@link CatalogoReglasDto#parametros()}.
 *
 * @param id   nombre técnico de la regla (el mismo que el id de su nodo en {@code /api/rules/schema})
 * @param rama {@code GLOBAL | RIEGO | INSUMO | MEDIASOMBRA | SEGUIMIENTO}
 */
public record ReglaCatalogoDto(
        String id,
        String label,
        String rama,
        int prioridad,
        List<String> parametros
) {}
