package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Reloj de test que se puede adelantar: el tiempo de los tests de despacho no depende del reloj real. */
public final class RelojDePrueba extends Clock {

    private volatile Instant ahora;

    public RelojDePrueba(Instant inicio) {
        this.ahora = inicio;
    }

    public void avanzar(Duration d) {
        ahora = ahora.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZonaHorariaVivero.ZONA;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
