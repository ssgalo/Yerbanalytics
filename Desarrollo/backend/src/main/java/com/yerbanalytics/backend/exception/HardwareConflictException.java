package com.yerbanalytics.backend.exception;

/**
 * Thrown when a hardware registration violates a uniqueness rule: duplicate serial/MAC or
 * a second actuator of the same type in one sector (HU-18 CA-03).
 * The controller translates this to a 409 response.
 */
public class HardwareConflictException extends RuntimeException {
    public HardwareConflictException(String message) {
        super(message);
    }
}
