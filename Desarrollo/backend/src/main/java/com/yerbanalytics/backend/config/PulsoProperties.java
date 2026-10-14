package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reintentos del pulso de sensado (design add-pulso-sensado-y-pasada-diaria D2). Una zona del pulso
 * recibe hasta {@code intentos} pedidos de {@code LEER_AHORA}, uno cada {@code reintentoSeg}; vencido el
 * último plazo queda sin respuesta en ese pulso.
 */
@ConfigurationProperties(prefix = "yerbanalytics.pulso")
public class PulsoProperties {

    /** Segundos entre dos pedidos a una zona que todavía no respondió. */
    private int reintentoSeg = 60;

    /** Pedidos por zona en un pulso, contando el primero. */
    private int intentos = 3;

    /** Cadencia del tick que reenvía los pedidos y cierra las zonas vencidas, en ms. */
    private long tickMs = 5000;

    public int getReintentoSeg() { return reintentoSeg; }
    public void setReintentoSeg(int v) { this.reintentoSeg = v; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int v) { this.intentos = v; }

    public long getTickMs() { return tickMs; }
    public void setTickMs(long v) { this.tickMs = v; }

    /** Ventana de respuesta de una zona: desde el primer pedido hasta que vence el último. */
    public int ventanaSeg() {
        return Math.max(1, reintentoSeg) * Math.max(1, intentos);
    }
}
