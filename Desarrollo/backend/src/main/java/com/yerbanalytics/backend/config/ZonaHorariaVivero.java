package com.yerbanalytics.backend.config;

import java.time.ZoneId;

/**
 * Zona horaria del vivero (San Ignacio, Misiones). Las ventanas y los ciclos de riego se evalúan
 * siempre en esta zona, sin depender de la zona del JVM: un servidor en UTC no puede correr el
 * "no regar de noche" tres horas.
 */
public final class ZonaHorariaVivero {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private ZonaHorariaVivero() {
    }
}
