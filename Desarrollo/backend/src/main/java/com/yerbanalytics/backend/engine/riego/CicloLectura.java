package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Ciclo de lectura del riego: franjas de {@code minutos} anclada a las 02:00 locales
 * ({@code reglas_v2} §1.2: con 240 min son las 02, 06, 10, 14, 18 y 22 h). Un sector recibe a lo sumo
 * un riego por ciclo (design D5). Si el intervalo no divide las 24 h, el último ciclo del día se corta
 * a las 02:00 y el día siguiente arranca de nuevo.
 */
public final class CicloLectura {

    private static final Logger log = LoggerFactory.getLogger(CicloLectura.class);

    /** Mínimo del intervalo de lectura (1 h, {@code reglas_v2} §11). */
    public static final int MINUTOS_MIN = 60;
    /** Máximo del intervalo de lectura (6 h, {@code reglas_v2} §11). */
    public static final int MINUTOS_MAX = 360;
    private static final LocalTime ANCLA = LocalTime.of(2, 0);

    /** Último valor fuera de rango avisado: el warn sale al cambiar, no en cada evaluación. */
    private static volatile Integer ultimoAvisado;

    private CicloLectura() {
    }

    /** Acota el intervalo guardado a 60–360 min y avisa (una vez por valor) si estaba fuera de rango. */
    public static int acotarMinutos(int minutos) {
        int acotado = Math.min(MINUTOS_MAX, Math.max(MINUTOS_MIN, minutos));
        if (acotado != minutos && !Integer.valueOf(minutos).equals(ultimoAvisado)) {
            ultimoAvisado = minutos;
            log.warn("El intervalo de sensado guardado ({} min) está fuera de {}-{} min: el ciclo de lectura usa {} min.",
                    minutos, MINUTOS_MIN, MINUTOS_MAX, acotado);
        }
        return acotado;
    }

    /** Inicio del ciclo de lectura que contiene {@code ahora}, según el intervalo (acotado a 60–360 min). */
    public static Instant inicio(Instant ahora, int minutos) {
        int paso = acotarMinutos(minutos);
        ZonedDateTime local = ahora.atZone(ZonaHorariaVivero.ZONA);
        ZonedDateTime ancla = local.toLocalDate().atTime(ANCLA).atZone(ZonaHorariaVivero.ZONA);
        if (local.isBefore(ancla)) {
            ancla = ancla.minusDays(1);
        }
        long transcurridos = ChronoUnit.MINUTES.between(ancla, local);
        return ancla.plusMinutes((transcurridos / paso) * paso).toInstant();
    }
}
