package com.yerbanalytics.backend.exception;

/** La revisión de un sector no se puede registrar (falta el detalle). El controller la traduce a {@code 400}. */
public class InvalidFungicideReviewException extends RuntimeException {
    public InvalidFungicideReviewException(String message) {
        super(message);
    }
}
