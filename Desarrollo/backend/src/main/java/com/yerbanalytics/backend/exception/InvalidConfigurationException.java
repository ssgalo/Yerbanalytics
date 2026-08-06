package com.yerbanalytics.backend.exception;

/**
 * Thrown when a proposed configuration violates a physiological or coherence constraint (HU-15 CA-03).
 * The controller translates this to a 400 response with the message.
 */
public class InvalidConfigurationException extends RuntimeException {
    public InvalidConfigurationException(String message) {
        super(message);
    }
}
