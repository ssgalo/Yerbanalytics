package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.Severity;

import java.util.Set;

/**
 * Cómo estaba el sector cuando se aplicó el tratamiento: contra esto se compara el diagnóstico de después.
 *
 * @param state     estado del diagnóstico que originó el tratamiento
 * @param severity  su severidad, o {@code null} si el diagnóstico no la tenía
 * @param neighbors los lindantes (ids de sector) que ya tenían ese mismo estado; los que aparezcan después son "nuevos"
 */
public record TreatmentBaseline(String state, Severity severity, Set<String> neighbors) {

    public TreatmentBaseline {
        neighbors = neighbors == null ? Set.of() : Set.copyOf(neighbors);
    }
}
