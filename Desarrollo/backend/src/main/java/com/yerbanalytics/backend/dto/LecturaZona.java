package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Lectura sensada de una macro-zona: lo que reportó su único nodo testigo. Todos los
 * sectores de la zona comparten esta lectura.
 *
 * @param metrics las 10 métricas ya evaluadas contra sus umbrales
 * @param dpv     déficit de presión de vapor, derivado de T y HR; {@code null} si la zona no tiene dato o el nodo
 *                está fuera de servicio. Informativo: no entra en {@code metrics} ni mueve el estado de los sectores
 * @param ts      instante de la lectura (epoch ms), {@code null} si nunca reportó
 * @param ago     antigüedad legible ("hace 4 min")
 * @param stale   el nodo superó el umbral de silencio: los valores no son vigentes
 */
public record LecturaZona(
        List<Metric> metrics,
        VaporDeficit dpv,
        Long ts,
        String ago,
        boolean stale
) {}
