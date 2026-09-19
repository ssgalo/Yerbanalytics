package com.yerbanalytics.backend.dto;

/**
 * Arista del DAG de reglas.
 *
 * @param id     ID único de la arista. Convención: "e_{source}_{target}".
 * @param source ID del nodo de origen.
 * @param target ID del nodo de destino.
 * @param label  Etiqueta semántica de la transición. Ej: "Continúa", "Bloquea", "Postpone".
 */
public record RuleEdgeDto(
        String id,
        String source,
        String target,
        String label
) {}
