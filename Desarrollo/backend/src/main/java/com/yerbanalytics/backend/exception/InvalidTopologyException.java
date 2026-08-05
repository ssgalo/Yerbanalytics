package com.yerbanalytics.backend.exception;

/**
 * Thrown when the topology generation receives a number of macro-zones or sectors outside
 * the valid limits (HU-18 CA-01).
 * The controller translates this to a 400 response.
 */
public class InvalidTopologyException extends RuntimeException {
    public InvalidTopologyException(String message) {
        super(message);
    }
}
