package com.yerbanalytics.backend.engine.overrides;

import java.util.Collection;

/** El override nombra una clave de cooldown o de bypass que la pieza no declara. */
public class InvalidOverrideKeyException extends RuntimeException {

    public InvalidOverrideKeyException(String name, String kind, Collection<String> unknown, Collection<String> declared) {
        super("La regla '" + name + "' no declara " + kind + " " + unknown + "; declara " + declared + ".");
    }
}
