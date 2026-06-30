package com.yerbanalytics.backend.service;

/**
 * Se lanza cuando la generación de topología recibe una cantidad de macro-zonas o de
 * sectores fuera de los límites válidos (HU-18 CA-01). El controller la traduce a un 400.
 */
public class TopologiaInvalidaException extends RuntimeException {
    public TopologiaInvalidaException(String message) {
        super(message);
    }
}
