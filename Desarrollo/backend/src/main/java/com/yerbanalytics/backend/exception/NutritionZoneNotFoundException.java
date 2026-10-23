package com.yerbanalytics.backend.exception;

/** La macro-zona del plan de nutrición no existe. El controller la traduce a {@code 404}. */
public class NutritionZoneNotFoundException extends RuntimeException {
    public NutritionZoneNotFoundException(String zoneId) {
        super("La macro-zona " + zoneId + " no existe.");
    }
}
