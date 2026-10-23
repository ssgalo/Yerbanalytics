package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Macro-zona del vivero. Dueña de la lectura sensada: hay un solo nodo testigo por
 * macro-zona, así que {@code lectura} y {@code nodo} aplican a todos sus sectores. {@code nutritionPlan} es el plan de
 * nutrición de la zona; {@code null} si el snapshot no lo arma.
 */
public record Zona(
        String id, String name, String sub, List<Sector> sectors,
        Integer sano, Integer alerta, Integer off, Integer total,
        LecturaZona lectura, NodoTestigo nodo,
        NutritionPlan nutritionPlan
) {

    /** Sin plan de nutrición: la firma de antes de N-01 (los tests y los llamadores que no lo arman). */
    public Zona(String id, String name, String sub, List<Sector> sectors, Integer sano, Integer alerta, Integer off,
                Integer total, LecturaZona lectura, NodoTestigo nodo) {
        this(id, name, sub, sectors, sano, alerta, off, total, lectura, nodo, null);
    }
}
