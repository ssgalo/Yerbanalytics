package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Evento que publica el ESP32 del riel (ver {@link ContratoRiel}). {@code posicion} es la lógica
 * (0 home, 1, 2) o {@code null} si quedó entre posiciones; {@code codigo} y {@code detalle} sólo
 * vienen en {@code ERROR}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoRiel(String commandId, String status, Integer posicion, Long pasos,
                         String codigo, String detalle) {
}
