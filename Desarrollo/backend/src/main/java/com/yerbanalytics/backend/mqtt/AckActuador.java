package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ack que publica el nodo al terminar un comando de riego, insumo o mediasombra (ver
 * {@link ContratoNodo} y la sección "Ack" de {@code contrato.h}). {@code status} es
 * {@code SUCCESS} o {@code ERROR}; {@code detalle.tipo} dice cómo terminó ({@code ok},
 * {@code falla_mecanica}, {@code reemplazado}…) y el resto de {@code detalle} trae lo que se hizo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AckActuador(String commandId, String status, Detalle detalle) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detalle(String tipo, Long durationSec, Double ml, Integer targetPct) {
    }

    /** {@code detalle.tipo}, o {@code null} si no vino. */
    public String tipoDetalle() {
        return detalle != null ? detalle.tipo() : null;
    }
}
