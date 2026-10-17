package com.yerbanalytics.backend.exception;

/** El bloqueo manual a reanudar no existe o ya está reanudado. El controller la traduce a {@code 404}. */
public class ManualLockNotFoundException extends RuntimeException {
    public ManualLockNotFoundException(String message) {
        super(message);
    }
}
