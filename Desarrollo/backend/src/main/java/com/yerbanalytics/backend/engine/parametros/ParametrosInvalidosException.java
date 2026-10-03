package com.yerbanalytics.backend.engine.parametros;

import java.util.List;

/** El lote de cambios no es válido: no se persistió nada. El controller lo traduce a un 400. */
public class ParametrosInvalidosException extends RuntimeException {

    private final List<ErrorParametro> errores;

    public ParametrosInvalidosException(List<ErrorParametro> errores) {
        super("Parámetros inválidos: " + errores);
        this.errores = List.copyOf(errores);
    }

    public List<ErrorParametro> getErrores() {
        return errores;
    }
}
