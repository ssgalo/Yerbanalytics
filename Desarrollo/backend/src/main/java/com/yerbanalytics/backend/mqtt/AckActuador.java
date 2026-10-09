package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * ACK que publica el nodo al terminar de cumplir un comando de actuador
 * ({@link ContratoNodo#TOPIC_ACK}). {@code status}: {@link ContratoNodo#STATUS_SUCCESS} o
 * {@link ContratoNodo#STATUS_ERROR}. {@code detalle} es un <b>objeto</b> ({@code {"tipo":"ok",…}}; en
 * el evento del riel, en cambio, es un string) y se lee como árbol JSON tolerante. {@code zonaId} y
 * {@code sectorId} no vienen en el payload: salen del tópico.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AckActuador(String commandId, String status, JsonNode detalle, String zonaId, String sectorId) {
}
