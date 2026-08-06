package com.yerbanalytics.backend.exception;

/**
 * Thrown when a hardware registration or replacement has missing or invalid data (HU-18 CA-02).
 * The controller translates this to a 400 response with the message.
 */
public class InvalidHardwareException extends RuntimeException {
    public InvalidHardwareException(String message) {
        super(message);
    }
}
