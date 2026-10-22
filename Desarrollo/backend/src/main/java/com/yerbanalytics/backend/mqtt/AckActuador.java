package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ack que publica el nodo al terminar de cumplir un comando de riego, insumo o mediasombra
 * ({@link ContratoNodo#TOPIC_ACK_SUSCRIPCION}; ver la sección "Ack" de {@code contrato.h}).
 * {@code status}: {@link ContratoNodo#ACK_SUCCESS} o {@link ContratoNodo#ACK_ERROR} (finales) o
 * {@link ContratoNodo#ACK_EN_CURSO} (intermedio: {@code en_cola} / {@code iniciado}). {@code detalle}
 * es un <b>objeto</b> ({@code {"tipo":"ok",…}}; en el evento del riel, en cambio, es un string) y se
 * lee como árbol JSON tolerante: {@code detalle.tipo} dice cómo terminó ({@code ok},
 * {@code falla_mecanica}, {@code reemplazado}…). {@code zonaId} y {@code sectorId} no vienen en el
 * payload: salen del tópico.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AckActuador(String commandId, String status, JsonNode detalle, String zonaId, String sectorId) {

    /** {@code true} si es un ack intermedio ({@link ContratoNodo#ACK_EN_CURSO}): no cierra el comando. */
    public boolean esIntermedio() {
        return ContratoNodo.ACK_EN_CURSO.equals(status);
    }

    /** {@code detalle.esperaMaxSec} de un {@code en_cola}, o 0 si no vino. */
    public long esperaMaxSec() {
        if (detalle == null || !detalle.isObject()) {
            return 0;
        }
        JsonNode espera = detalle.get("esperaMaxSec");
        return espera == null || !espera.isNumber() ? 0 : Math.max(0, espera.asLong());
    }

    /** {@code detalle.tipo}, o {@code null} si no vino. */
    public String tipoDetalle() {
        if (detalle == null || !detalle.isObject()) {
            return null;
        }
        JsonNode tipo = detalle.get("tipo");
        return tipo == null || tipo.isNull() ? null : tipo.asText();
    }
}
