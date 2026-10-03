package com.yerbanalytics.backend.engine.parametros;

import java.util.List;

/**
 * Cualquier componente que lee parámetros del catálogo: las reglas del motor, y también quien
 * ejecuta sin ser una regla (el despacho de riego lee {@code riego.sectores-simultaneos}).
 *
 * <p>El catálogo valida al arrancar que todo lo declarado exista y arma con esto el campo
 * {@code usadoPor}, de modo que ningún parámetro figure "sin uso" mientras alguien lo lee.
 */
public interface ConsumidorParametros {

    /** Nombre técnico, el que figura en {@code usadoPor}. */
    String name();

    /** Parámetros del catálogo que este componente lee. Por defecto ninguno. */
    default List<DefinicionParametro> parametros() {
        return List.of();
    }
}
