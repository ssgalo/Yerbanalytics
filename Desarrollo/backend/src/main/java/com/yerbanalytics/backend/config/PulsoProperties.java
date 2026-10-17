package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cadencia técnica del pulso de sensado (design add-pulso-sensado-y-pasada-diaria). Los plazos de espera y los
 * reintentos ya no viven acá: son los parámetros {@code sensado.*} del catálogo, editables por el agrónomo
 * (ver {@code SensingParameters}).
 */
@ConfigurationProperties(prefix = "yerbanalytics.pulso")
public class PulsoProperties {

    /** Cadencia del tick que reenvía los pedidos y cierra las zonas vencidas, en ms. */
    private long tickMs = 5000;

    public long getTickMs() { return tickMs; }
    public void setTickMs(long v) { this.tickMs = v; }
}
