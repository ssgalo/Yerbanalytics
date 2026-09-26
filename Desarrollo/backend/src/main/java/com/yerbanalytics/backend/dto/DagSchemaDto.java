package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Esquema base del DAG del motor de reglas.
 *
 * <p>Este DTO representa la topología estática del pipeline de evaluación de reglas.
 * Es la estructura que el frontend utiliza para renderizar el grafo interactivo y
 * para reconstruir dinámicamente el camino tomado al seleccionar un evento del historial.
 *
 * <p>El grafo es dirigido y acíclico (DAG). Cada regla tiene una arista de "Continúa"
 * hacia la siguiente regla de mayor prioridad, y una arista de "Bloquea" hacia el nodo
 * terminal de aborto (cuando la regla emite una acción bloqueante).
 *
 * @param nodes lista de nodos del DAG (reglas + nodos especiales start/abort/success)
 * @param edges lista de aristas dirigidas entre los nodos
 */
public record DagSchemaDto(
        List<RuleNodeDto> nodes,
        List<RuleEdgeDto> edges
) {}
