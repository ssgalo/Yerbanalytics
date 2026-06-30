package com.yerbanalytics.backend.service;

/**
 * Se lanza cuando un alta de hardware viola una regla de unicidad: serial/MAC duplicado o
 * un segundo actuador del mismo tipo en un sector (HU-18 CA-03). El controller la traduce
 * a un 409 con su mensaje.
 */
public class HardwareConflictoException extends RuntimeException {
    public HardwareConflictoException(String message) {
        super(message);
    }
}
