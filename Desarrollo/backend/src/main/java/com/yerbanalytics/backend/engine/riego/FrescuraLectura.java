package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.model.ZonaEntity;

/**
 * Antigüedad de la lectura de una macro-zona: UNA sola definición para quien decide ({@code StaleSensorRule},
 * que la muestra en la traza) y quien ejecuta ({@code DespachoRiego}, que la revalida antes de abrir cada
 * válvula). Las dos comparan contra el mismo parámetro del catálogo, {@code seguridad.antiguedad-max-lectura}.
 *
 * <p>La humedad de sustrato se mide aparte de la lectura: si la sonda falla, el nodo sigue publicando el
 * resto, la zona figura fresca y el backend conserva la humedad vieja. Regar con ese valor congelado sería
 * regar a ciegas.
 */
public final class FrescuraLectura {

    private FrescuraLectura() {
    }

    /** Segundos desde la última lectura de la zona, o {@code null} si no hay zona o nunca reportó. */
    public static Double antiguedadSegundos(ZonaEntity zona, long ahoraMs) {
        if (zona == null || zona.getLastReadingTime() == null) {
            return null;
        }
        return (ahoraMs - zona.getLastReadingTime()) / 1000.0;
    }

    /** Segundos desde la última humedad de sustrato recibida, o {@code null} si no hay zona o nunca llegó. */
    public static Double antiguedadHumedadSegundos(ZonaEntity zona, long ahoraMs) {
        if (zona == null || zona.getHumSusTs() == null) {
            return null;
        }
        return (ahoraMs - zona.getHumSusTs()) / 1000.0;
    }

    /** {@code true} si la lectura Y la humedad de sustrato existen y no superan {@code maxSegundos}. */
    public static boolean vigente(ZonaEntity zona, long ahoraMs, double maxSegundos) {
        Double lectura = antiguedadSegundos(zona, ahoraMs);
        Double humedad = antiguedadHumedadSegundos(zona, ahoraMs);
        return lectura != null && lectura <= maxSegundos && humedad != null && humedad <= maxSegundos;
    }
}
