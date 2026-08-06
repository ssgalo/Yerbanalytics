package com.yerbanalytics.backend.exception;

/**
 * Thrown when attempting to generate a topology over a nursery that already has a grid loaded
 * without explicitly requesting regeneration (HU-18 CA-01).
 * The controller translates this to a 409 response.
 */
public class TopologyConflictException extends RuntimeException {
    public TopologyConflictException(String message) {
        super(message);
    }
}
