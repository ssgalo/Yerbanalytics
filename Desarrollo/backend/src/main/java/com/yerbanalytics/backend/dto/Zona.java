package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Macro-zona del vivero. Dueña de la lectura sensada: hay un solo nodo testigo por
 * macro-zona, así que {@code lectura} y {@code nodo} aplican a todos sus sectores.
 */
public record Zona(
        String id, String name, String sub, List<Sector> sectors,
        Integer sano, Integer alerta, Integer off, Integer total,
        LecturaZona lectura, NodoTestigo nodo
) {}
