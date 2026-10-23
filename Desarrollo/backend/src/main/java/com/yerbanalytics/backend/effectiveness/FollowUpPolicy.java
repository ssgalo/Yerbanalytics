package com.yerbanalytics.backend.effectiveness;

/**
 * Cuánto se espera para juzgar cada tipo de acción: lo que el historial necesita al abrir un seguimiento, sin conocer el
 * catálogo de parámetros ni los overrides de demo. La implementa {@link EffectivenessService}.
 */
public interface FollowUpPolicy {

    /**
     * Cuánto después de la acción se evalúa. Para el riego es la espera de la lectura de verificación, que corre desde que el riego
     * cierra: quien abre el seguimiento le suma la duración de la válvula.
     */
    long latencyMs(EffectivenessKind kind);

    /** La espera como la muestra el historial ({@code 30 min}, {@code 14 d}). */
    default String latencyLabel(EffectivenessKind kind) {
        return LatencyLabel.of(latencyMs(kind));
    }
}
