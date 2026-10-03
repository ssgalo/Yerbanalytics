package com.yerbanalytics.backend.engine.parametros;

/** Un texto no corresponde al tipo, rango o formato de un parámetro. El mensaje es para el usuario. */
public class ValorParametroInvalidoException extends IllegalArgumentException {
    public ValorParametroInvalidoException(String message) {
        super(message);
    }
}
