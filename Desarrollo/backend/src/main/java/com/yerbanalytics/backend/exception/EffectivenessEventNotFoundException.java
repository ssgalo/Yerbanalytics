package com.yerbanalytics.backend.exception;

/** El evento del historial cuya revisión se registra no existe. El controller la traduce a {@code 404}. */
public class EffectivenessEventNotFoundException extends RuntimeException {
    public EffectivenessEventNotFoundException(String eventId) {
        super("El evento " + eventId + " no existe.");
    }
}
