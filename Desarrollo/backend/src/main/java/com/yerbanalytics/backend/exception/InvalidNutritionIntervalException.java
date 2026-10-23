package com.yerbanalytics.backend.exception;

/** El intervalo del plan de nutrición de una macro-zona no es válido (falta, no es un número entero o está fuera de rango). El controller la traduce a {@code 400}. */
public class InvalidNutritionIntervalException extends RuntimeException {
    public InvalidNutritionIntervalException(String message) {
        super(message);
    }
}
