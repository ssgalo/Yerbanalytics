package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.RuleBranch;

import java.util.List;

/**
 * Lo que pasó con una regla en una evaluación.
 *
 * @param bloqueadaPor regla que cortó la rama ({@code OMITIDA_RAMA_BLOQUEADA}) o el pipeline
 *                     ({@code NO_ALCANZADA}); {@code null} si la regla se evaluó
 * @param error        {@code Clase: mensaje} de la excepción si la regla lanzó ({@code ERROR}); sino {@code null}
 * @param supuesto     lo que la regla asumió porque no había pronóstico (O-01…O-03), p. ej. «Sin pronóstico: no llueve…»;
 *                     {@code null} si usó el pronóstico o no lo necesita
 */
public record TrazaRegla(
        String ruleId,
        RuleBranch rama,
        int prioridad,
        EstadoRegla estado,
        List<Comparacion> comparaciones,
        List<AccionTrazada> acciones,
        String bloqueadaPor,
        String error,
        String supuesto
) {

    /** Una regla que no lanzó ni asumió nada: sin error y sin supuesto. */
    public TrazaRegla(String ruleId, RuleBranch rama, int prioridad, EstadoRegla estado,
                      List<Comparacion> comparaciones, List<AccionTrazada> acciones, String bloqueadaPor) {
        this(ruleId, rama, prioridad, estado, comparaciones, acciones, bloqueadaPor, null, null);
    }

    /** Con el error de la excepción, sin supuesto. */
    public TrazaRegla(String ruleId, RuleBranch rama, int prioridad, EstadoRegla estado,
                      List<Comparacion> comparaciones, List<AccionTrazada> acciones, String bloqueadaPor, String error) {
        this(ruleId, rama, prioridad, estado, comparaciones, acciones, bloqueadaPor, error, null);
    }
}
