package com.yerbanalytics.backend.biotic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * «El mismo diagnóstico en N capturas diarias seguidas» (D-3). Hay una pasada del riel por día, así que una captura
 * equivale a un día: si faltó una pasada, ese día no tiene captura y la racha se corta.
 *
 * <p>Lógica pura. De cada día vale la <b>última</b> captura (la más reciente es la que tiene el sector); el día cuenta si
 * esa captura cumple el filtro. La racha corre hacia atrás desde el día de la captura más nueva y termina en el primer día
 * que no cumple o que no tiene captura.
 */
public final class ConsecutiveCaptures {

    private ConsecutiveCaptures() {
    }

    /** Lo que se necesita saber de un diagnóstico pasado. */
    public record Capture(String estado, Double conf, long ts) {
    }

    /**
     * @param newestFirst las capturas del sector, de la más nueva a la más vieja
     * @param matches     qué capturas cuentan (mismo diagnóstico, confianza suficiente…)
     * @param zone        zona horaria del vivero: define dónde empieza y termina el día
     * @return cuántos días seguidos, hasta el de la captura más nueva, cumplen el filtro; {@code 0} si no hay capturas o la
     *         más nueva no lo cumple
     */
    public static int count(List<Capture> newestFirst, Predicate<Capture> matches, ZoneId zone) {
        List<Capture> lastOfEachDay = new ArrayList<>();
        LocalDate lastDay = null;
        for (Capture capture : newestFirst) {
            LocalDate day = Instant.ofEpochMilli(capture.ts()).atZone(zone).toLocalDate();
            if (!day.equals(lastDay)) {
                lastOfEachDay.add(capture);
                lastDay = day;
            }
        }
        int streak = 0;
        LocalDate expected = null;
        for (Capture capture : lastOfEachDay) {
            LocalDate day = Instant.ofEpochMilli(capture.ts()).atZone(zone).toLocalDate();
            boolean consecutive = expected == null || day.equals(expected);
            if (!consecutive || !matches.test(capture)) {
                break;
            }
            streak++;
            expected = day.minusDays(1);
        }
        return streak;
    }
}
