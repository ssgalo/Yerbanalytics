package com.yerbanalytics.backend.exception;

/** Carga de tanque inválida (litros o capacidad que no cierran, o un desborde). El controller la traduce a {@code 400}. */
public class InvalidTankLoadException extends RuntimeException {
    public InvalidTankLoadException(String message) {
        super(message);
    }
}
