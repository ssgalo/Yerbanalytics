package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.RuleBranch;

import java.util.List;

/**
 * Lo que pasó con una regla en una evaluación.
 *
 * @param bloqueadaPor regla que cortó la rama ({@code OMITIDA_RAMA_BLOQUEADA}) o el pipeline
 *                     ({@code NO_ALCANZADA}); {@code null} si la regla se evaluó
 */
public record TrazaRegla(
        String ruleId,
        RuleBranch rama,
        int prioridad,
        EstadoRegla estado,
        List<Comparacion> comparaciones,
        List<AccionTrazada> acciones,
        String bloqueadaPor
) {}
