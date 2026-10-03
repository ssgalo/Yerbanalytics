package com.yerbanalytics.backend.engine.parametros;

import java.util.List;
import java.util.function.Predicate;

/**
 * Restricción que involucra a varios parámetros (p. ej. {@code crítico < umbral < objetivo}).
 * Se evalúa sobre el CONJUNTO resultante de un guardado, no sobre cada valor suelto, así un
 * lote que mueve dos parámetros a la vez se valida contra su estado final.
 *
 * @param claves    parámetros que intervienen (todos deben existir en el catálogo)
 * @param predicado {@code true} si el conjunto cumple la restricción
 * @param mensaje   texto para el usuario cuando no se cumple
 */
public record RestriccionCruzada(List<String> claves, Predicate<ParametrosVigentes> predicado, String mensaje) {

    public boolean cumple(ParametrosVigentes valores) {
        return predicado.test(valores);
    }
}
