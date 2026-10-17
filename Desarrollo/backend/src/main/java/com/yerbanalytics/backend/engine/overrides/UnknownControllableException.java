package com.yerbanalytics.backend.engine.overrides;

/** No existe una pieza controlable con ese nombre. */
public class UnknownControllableException extends RuntimeException {

    public UnknownControllableException(String name) {
        super("No existe la regla '" + name + "'.");
    }
}
