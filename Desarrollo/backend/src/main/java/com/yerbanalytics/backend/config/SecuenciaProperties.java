package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tiempos de las secuencias guionadas de la Demo Expo (design add-secuencias-demo-expo §2.2). */
@ConfigurationProperties(prefix = "yerbanalytics.secuencia")
public class SecuenciaProperties {

    /**
     * Plazo para el ACK del OFF de la válvula (cerrar es instantáneo: esto cubre la latencia del
     * broker). El ACK del ON, en cambio, llega al <em>final</em> del riego: ver {@link #margenAckRiegoSeg}.
     */
    private int timeoutAckValvulaSeg = 10;

    /** Margen sobre la duración N del riego para que llegue el ACK del ON (que el firmware manda al terminar). */
    private int margenAckRiegoSeg = 15;

    /** Plazo para el ACK de la mediasombra: el firmware se rinde a los 30 s sin final de carrera. */
    private int timeoutAckMediasombraSeg = 45;

    /** Plazo para que llegue la telemetría pedida con "leer ahora". */
    private int timeoutLecturaSeg = 20;

    /** Cadencia del tick del orquestador, en ms. */
    private long tickMs = 1000;

    public int getTimeoutAckValvulaSeg() { return timeoutAckValvulaSeg; }
    public void setTimeoutAckValvulaSeg(int v) { this.timeoutAckValvulaSeg = v; }

    public int getMargenAckRiegoSeg() { return margenAckRiegoSeg; }
    public void setMargenAckRiegoSeg(int v) { this.margenAckRiegoSeg = v; }

    public int getTimeoutAckMediasombraSeg() { return timeoutAckMediasombraSeg; }
    public void setTimeoutAckMediasombraSeg(int v) { this.timeoutAckMediasombraSeg = v; }

    public int getTimeoutLecturaSeg() { return timeoutLecturaSeg; }
    public void setTimeoutLecturaSeg(int v) { this.timeoutLecturaSeg = v; }

    public long getTickMs() { return tickMs; }
    public void setTickMs(long v) { this.tickMs = v; }
}
