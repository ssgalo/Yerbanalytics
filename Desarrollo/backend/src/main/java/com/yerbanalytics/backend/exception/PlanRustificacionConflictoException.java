package com.yerbanalytics.backend.exception;

/**
 * La zona ya tiene un plan de rustificación vigente (al iniciar) o no tiene ninguno (al eliminar). El controller la
 * traduce a {@code 409}.
 */
public class PlanRustificacionConflictoException extends RuntimeException {
    public PlanRustificacionConflictoException(String message) {
        super(message);
    }
}
