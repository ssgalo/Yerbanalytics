package com.yerbanalytics.backend.biotic;

import java.util.Optional;
import java.util.Set;

/**
 * Los dos tipos de daño biótico que reglas_v2 trata distinto, según el estado del diagnóstico:
 * <ul>
 *   <li>{@link #FUNGAL}: {@code Daño fúngico} (F-F1).</li>
 *   <li>{@link #PEST}: {@code Plaga foliar} y {@code Ácaro}, que es una plaga foliar (F-P1, D-1).</li>
 * </ul>
 * El {@code Daño biótico} genérico no es ninguno de los dos: no se sabe qué condición lo justifica, así que no dispara un
 * tratamiento (decisión por defecto de D-1, a validar con el vivero).
 */
public enum BioticKind {

    FUNGAL("daño fúngico", Set.of("Daño fúngico")),
    PEST("plaga foliar", Set.of("Plaga foliar", "Ácaro"));

    private final String label;
    private final Set<String> states;

    BioticKind(String label, Set<String> states) {
        this.label = label;
        this.states = states;
    }

    /** El tipo al que pertenece el estado de un diagnóstico; vacío si no es de ninguno. */
    public static Optional<BioticKind> of(String state) {
        for (BioticKind kind : values()) {
            if (kind.matches(state)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public boolean matches(String state) {
        return state != null && states.contains(state);
    }

    /** Cómo lo nombra el operador en las alertas. */
    public String label() {
        return label;
    }
}
