package com.yerbanalytics.backend.exception;

/** La revisión de una acción sin efectividad no se puede registrar (falta el detalle). El controller la traduce a {@code 400}. */
public class InvalidEffectivenessReviewException extends RuntimeException {
    public InvalidEffectivenessReviewException(String message) {
        super(message);
    }
}
