package com.yerbanalytics.backend.exception;

/**
 * Se lanza cuando un criterio de diagnóstico no es válido (bandas no enteras o no crecientes,
 * estados faltantes o repetidos, criticidad que no es una permutación). El controller lo traduce
 * a {@code 400} con el mensaje, apto para mostrar.
 */
public class InvalidDiagnosisCriteriaException extends RuntimeException {
    public InvalidDiagnosisCriteriaException(String message) {
        super(message);
    }
}
