package com.yerbanalytics.backend.service;

/**
 * Se lanza cuando se intenta generar una topología sobre un vivero que ya tiene una grilla
 * cargada sin pedir regenerar explícitamente (HU-18 CA-01). El controller la traduce a 409.
 */
public class TopologiaConflictoException extends RuntimeException {
    public TopologiaConflictoException(String message) {
        super(message);
    }
}
