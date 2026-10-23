package com.yerbanalytics.backend.exception;

/** Un plan de rustificación mal armado (fecha, etapas, ventanas). El controller la traduce a {@code 400}. */
public class PlanRustificacionInvalidoException extends RuntimeException {
    public PlanRustificacionInvalidoException(String message) {
        super(message);
    }
}
