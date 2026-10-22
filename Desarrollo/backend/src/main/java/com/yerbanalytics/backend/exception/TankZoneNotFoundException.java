package com.yerbanalytics.backend.exception;

/** La macro-zona del tanque no existe. El controller la traduce a {@code 404}. */
public class TankZoneNotFoundException extends RuntimeException {
    public TankZoneNotFoundException(String zoneId) {
        super("La macro-zona " + zoneId + " no existe.");
    }
}
