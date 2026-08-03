package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Lectura sensada de una macro-zona: lo que reportó su único nodo testigo. Todos los
 * sectores de la zona comparten esta lectura.
 *
 * @param metrics las 10 métricas ya evaluadas contra sus umbrales
 * @param ts      instante de la lectura (epoch ms), {@code null} si nunca reportó
 * @param ago     antigüedad legible ("hace 4 min")
 * @param stale   el nodo superó el umbral de silencio: los valores no son vigentes
 */
public record LecturaZona(
        List<Metric> metrics,
        Long ts,
        String ago,
        boolean stale
) {}
