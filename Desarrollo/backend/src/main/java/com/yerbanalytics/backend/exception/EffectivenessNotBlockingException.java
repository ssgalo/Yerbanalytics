package com.yerbanalytics.backend.exception;

/** El evento no es una acción sin efectividad que siga bloqueando: no hay nada que revisar. El controller la traduce a {@code 409}. */
public class EffectivenessNotBlockingException extends RuntimeException {
    public EffectivenessNotBlockingException(String eventId) {
        super("El evento " + eventId + " no es una acción sin efectividad que siga bloqueando.");
    }
}
