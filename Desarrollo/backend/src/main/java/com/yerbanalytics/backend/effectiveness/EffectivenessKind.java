package com.yerbanalytics.backend.effectiveness;

import java.util.Optional;

/**
 * Las acciones cuya efectividad se evalúa (reglas_v2 §9) y el alcance de su bloqueo (S-06): el riego bloquea a toda la
 * macro-zona; el fertilizante correctivo y el fitosanitario, al sector. La mediasombra, el plan de nutrición y N-02 no se
 * evalúan, y por lo tanto no tienen un tipo acá.
 */
public enum EffectivenessKind {

    IRRIGATION("Riego", true),
    CORRECTIVE_FERTILIZER("Fertilizante correctivo", false),
    FUNGICIDE("Fitosanitario", false);

    private final String label;
    private final boolean zoneScoped;

    EffectivenessKind(String label, boolean zoneScoped) {
        this.label = label;
        this.zoneScoped = zoneScoped;
    }

    public String label() {
        return label;
    }

    /** {@code true} si "sin efectividad" bloquea a la macro-zona entera; {@code false} si sólo al sector. */
    public boolean isZoneScoped() {
        return zoneScoped;
    }

    /** El tipo guardado en el evento del historial; vacío si no hay o no se reconoce. */
    public static Optional<EffectivenessKind> parse(String stored) {
        if (stored == null) {
            return Optional.empty();
        }
        for (EffectivenessKind kind : values()) {
            if (kind.name().equals(stored)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
