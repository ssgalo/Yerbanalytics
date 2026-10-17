package com.yerbanalytics.backend.exception;

/** Ya hay un bloqueo manual vigente sobre ese alcance. El controller la traduce a {@code 409}. */
public class ManualLockConflictException extends RuntimeException {
    public ManualLockConflictException(String message) {
        super(message);
    }
}
