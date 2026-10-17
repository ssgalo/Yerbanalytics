package com.yerbanalytics.backend.readings;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Qué pasó con una lectura al validarla (S-03): lo que se acepta y lo que no.
 *
 * @param accepted  las métricas válidas, las únicas que se escriben en la zona
 * @param pending   inválidas con relecturas todavía disponibles: ya se pidió otra lectura
 * @param discarded inválidas que agotaron sus relecturas: no se usan en este ciclo (y quedó la alerta)
 */
public record ReadingCheck(ReadingMetrics accepted, Set<String> pending, Set<String> discarded) {

    public ReadingCheck {
        pending = Set.copyOf(pending);
        discarded = Set.copyOf(discarded);
    }

    /** Sin nada que objetar: toda la lectura se acepta. */
    public static ReadingCheck allAccepted(ReadingMetrics reading) {
        return new ReadingCheck(reading, Set.of(), Set.of());
    }

    /**
     * {@code true} si falta una relectura que puede corregir alguna métrica: esta lectura todavía no es la
     * respuesta definitiva de la zona. Lo que decide el motor se espera a que llegue.
     */
    public boolean awaitsReread() {
        return !pending.isEmpty();
    }

    /** Las métricas que quedaron afuera de la lectura, por pendientes o por descartadas. */
    public Set<String> invalid() {
        Set<String> all = new LinkedHashSet<>(pending);
        all.addAll(discarded);
        return all;
    }
}
