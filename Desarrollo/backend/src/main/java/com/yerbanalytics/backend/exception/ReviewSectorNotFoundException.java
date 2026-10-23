package com.yerbanalytics.backend.exception;

/** El sector cuya revisión se registra no existe. El controller la traduce a {@code 404}. */
public class ReviewSectorNotFoundException extends RuntimeException {
    public ReviewSectorNotFoundException(String sectorId) {
        super("El sector " + sectorId + " no existe.");
    }
}
