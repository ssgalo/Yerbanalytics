package com.yerbanalytics.backend.service;

/**
 * Se lanza cuando un alta/recambio de hardware tiene datos faltantes o inválidos (HU-18
 * CA-02). El controller la traduce a un 400 con su mensaje.
 */
public class HardwareInvalidoException extends RuntimeException {
    public HardwareInvalidoException(String message) {
        super(message);
    }
}
