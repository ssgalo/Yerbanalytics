package com.yerbanalytics.backend.engine;

import java.util.Optional;

/**
 * La severidad de un diagnóstico en el vocabulario de reglas_v2 (leve / moderada / alta); la usan todas las reglas por
 * diagnóstico. La plataforma la guarda como Baja / Media / Alta (CLAUDE.md §2): este es el único lugar donde se traduce,
 * así ninguna regla compara textos.
 */
public enum Severity {

    MILD("Baja"),
    MODERATE("Media"),
    HIGH("Alta");

    private final String stored;

    Severity(String stored) {
        this.stored = stored;
    }

    /** La severidad guardada en el diagnóstico; vacío si el diagnóstico no tiene ({@code —}, {@code null}) o no se reconoce. */
    public static Optional<Severity> parse(String stored) {
        for (Severity s : values()) {
            if (s.stored.equals(stored)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** «Severidad moderada o alta»: la que por sí sola justifica un fitosanitario. */
    public boolean isModerateOrHigher() {
        return this != MILD;
    }

    public boolean isHigh() {
        return this == HIGH;
    }
}
