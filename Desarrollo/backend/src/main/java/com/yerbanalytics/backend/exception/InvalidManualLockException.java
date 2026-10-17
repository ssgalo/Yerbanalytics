package com.yerbanalytics.backend.exception;

/** Solicitud de bloqueo manual inválida (sin zona o motivo, o con un alcance que no existe). El controller la traduce a {@code 400}. */
public class InvalidManualLockException extends RuntimeException {
    public InvalidManualLockException(String message) {
        super(message);
    }
}
