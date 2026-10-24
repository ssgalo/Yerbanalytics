package com.yerbanalytics.backend.engine.parametros;

import java.util.List;
import java.util.Map;

/**
 * Cualquier componente que lee parámetros del catálogo: las reglas del motor, y también quien ejecuta sin ser una regla
 * (el despacho de riego lee {@code riego.sectores-simultaneos}).
 *
 * <p>Cada consumidor tiene su <b>copia</b> de cada parámetro que declara en {@link #parametros()}: editarla no cambia la
 * de otro (rediseno-motor-reglas D-1). El catálogo valida al arrancar que todo lo declarado exista.
 */
public interface ConsumidorParametros {

    /** Nombre técnico: el dueño de la copia ({@code regla} en la API de parámetros). */
    String name();

    /** Parámetros propios: los que tienen copia de este componente y se editan en su fila. Por defecto ninguno. */
    default List<DefinicionParametro> parametros() {
        return List.of();
    }

    /**
     * Parámetros que este componente lee de la copia de otra pieza (D-2): quien ejecuta o revalida lo que una regla
     * decidió no debe poder discrepar con ella. Clave → {@link #name()} de la pieza dueña, que tiene que declararla en
     * sus {@link #parametros()} (lo valida el catálogo al arrancar). Estas claves no figuran como propias ni se editan
     * acá. Por defecto ninguno.
     */
    default Map<DefinicionParametro, String> parametrosDe() {
        return Map.of();
    }
}
