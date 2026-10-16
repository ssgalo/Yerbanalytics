package com.yerbanalytics.backend.exception;

/**
 * Se lanza cuando se consulta un diagnóstico que no está persistido (incluye los {@code DG-}
 * que el snapshot deriva del sector). El controller lo traduce a {@code 404}.
 */
public class DiagnosisNotFoundException extends RuntimeException {
    public DiagnosisNotFoundException(String message) {
        super(message);
    }
}
