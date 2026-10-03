package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tiempos del planificador de pasadas del riel (design add-pasada-riel §2.1). */
@ConfigurationProperties(prefix = "yerbanalytics.pasada")
public class PasadaProperties {

    /** Sin ningún evento del riel: a este plazo se republica el comando, al doble se da por caído. */
    private int timeoutAceptacionSeg = 5;

    /** Plazo máximo de un movimiento (cubre pos2→home de 42 s y un homing completo de 80 s). */
    private int timeoutMovimientoSeg = 120;

    /** Plazo máximo de una foto (3 intentos de 60 s de la orden más el barrido del watchdog). */
    private int timeoutCapturaSeg = 240;

    /** Cadencia del tick del orquestador, en ms. */
    private long tickMs = 1000;

    public int getTimeoutAceptacionSeg() { return timeoutAceptacionSeg; }
    public void setTimeoutAceptacionSeg(int v) { this.timeoutAceptacionSeg = v; }

    public int getTimeoutMovimientoSeg() { return timeoutMovimientoSeg; }
    public void setTimeoutMovimientoSeg(int v) { this.timeoutMovimientoSeg = v; }

    public int getTimeoutCapturaSeg() { return timeoutCapturaSeg; }
    public void setTimeoutCapturaSeg(int v) { this.timeoutCapturaSeg = v; }

    public long getTickMs() { return tickMs; }
    public void setTickMs(long v) { this.tickMs = v; }
}
