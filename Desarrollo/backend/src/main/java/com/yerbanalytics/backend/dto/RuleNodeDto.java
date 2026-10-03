package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Nodo del DAG de reglas (topología del motor).
 *
 * @param id       ID único del nodo. Para reglas: el nombre de la clase (ej: "ClimaOverrideRule").
 *                 Para los nodos especiales: "start", "abort", "success".
 * @param label    Etiqueta legible para el usuario.
 * @param type     Tipo de nodo para el renderizador ("input", "default", "output").
 * @param priority Prioridad del nodo (-1 para start, 999 para terminales). Usado por el
 *                 frontend para determinar el coloreado reactivo al seleccionar un evento.
 * @param branch   Rama a la que pertenece el nodo.
 * @param parametros Claves de los parámetros del catálogo que declara la regla; vacía en los
 *                 nodos especiales (start, abort, success).
 */
public record RuleNodeDto(
        String id,
        String label,
        String type,
        int priority,
        String branch,
        List<String> parametros
) {}
