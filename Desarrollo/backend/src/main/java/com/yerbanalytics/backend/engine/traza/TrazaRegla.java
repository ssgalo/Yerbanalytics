package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.RuleBranch;

import java.util.List;

/**
 * Lo que pasó con una regla en una evaluación.
 *
 * @param bloqueadaPor regla que cortó la rama ({@code OMITIDA_RAMA_BLOQUEADA}) o el pipeline
 *                     ({@code NO_ALCANZADA}); {@code null} si la regla se evaluó
 * @param error        {@code Clase: mensaje} de la excepción si la regla lanzó ({@code ERROR}); sino {@code null}
 */
public record TrazaRegla(
        String ruleId,
        RuleBranch rama,
        int prioridad,
        EstadoRegla estado,
        List<Comparacion> comparaciones,
        List<AccionTrazada> acciones,
        String bloqueadaPor,
        String error
) {

    /** Una regla que no lanzó: sin error. */
    public TrazaRegla(String ruleId, RuleBranch rama, int prioridad, EstadoRegla estado,
                      List<Comparacion> comparaciones, List<AccionTrazada> acciones, String bloqueadaPor) {
        this(ruleId, rama, prioridad, estado, comparaciones, acciones, bloqueadaPor, null);
    }
}
