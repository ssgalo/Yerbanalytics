package com.yerbanalytics.backend.engine.parametros;

/**
 * Una regla intentó leer un parámetro que no declaró en {@code Rule.parametros()}. Es un error
 * de programación (no de datos): la relación regla → parámetros es una garantía verificable,
 * y de ella se arman la vista "por regla" y {@code usadoPor}.
 */
public class ParametroNoDeclaradoException extends IllegalStateException {

    public ParametroNoDeclaradoException(String regla, String clave) {
        super("La regla " + regla + " leyó el parámetro " + clave + " sin declararlo en parametros().");
    }
}
