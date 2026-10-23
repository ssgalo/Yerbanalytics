package com.yerbanalytics.backend.effectiveness;

import java.util.Set;

/**
 * Qué acciones tiene bloqueadas un sector por haber quedado «sin efectividad» (S-06), la vista que el motor arma <b>una vez por
 * zona</b> para que las reglas no consulten el historial. El riego bloquea a toda la macro-zona; el fertilizante correctivo y el
 * fitosanitario, sólo al sector que los recibió. La mediasombra no se evalúa, así que nunca figura.
 *
 * @param irrigation el último riego de la macro-zona quedó sin efectividad y nadie registró la revisión
 * @param treatments los tratamientos del sector (fertilizante correctivo, fitosanitario) en la misma situación
 */
public record EffectivenessBlocks(boolean irrigation, Set<EffectivenessKind> treatments) {

    /** Nada bloqueado. Es lo que ve un contexto que no arma los bloqueos (los tests de reglas sueltas). */
    public static final EffectivenessBlocks NONE = new EffectivenessBlocks(false, Set.of());

    public EffectivenessBlocks {
        treatments = treatments == null ? Set.of() : Set.copyOf(treatments);
    }

    public boolean blocks(EffectivenessKind kind) {
        return kind == EffectivenessKind.IRRIGATION ? irrigation : treatments.contains(kind);
    }
}
