package com.yerbanalytics.backend.engine.traza;

import java.time.Instant;
import java.util.List;

/**
 * Traza de una evaluación del motor para un sector: todas las reglas registradas, en orden de
 * prioridad. Inmutable, así se publica entre hilos con un reemplazo atómico en el
 * {@link TrazaEvaluacionStore}.
 *
 * @param zonaId         {@code null} si el sector no tiene zona
 * @param parametrosHash huella de los valores vigentes usados (dos trazas con la misma huella
 *                       se evaluaron contra los mismos umbrales)
 */
public record TrazaEvaluacion(
        String sectorId,
        String zonaId,
        OrigenEvaluacion origen,
        Instant ts,
        String parametrosHash,
        List<TrazaRegla> reglas
) {}
