package com.yerbanalytics.backend.engine;

import java.util.List;

/**
 * Contrato que toda regla de negocio del motor debe cumplir.
 *
 * <p>El {@link RuleOrchestrator} itera las reglas registradas como {@code @Component}
 * ordenadas ascendentemente por {@link #priority()}. Una regla de prioridad baja
 * (bloqueante) puede emitir una acción de tipo {@code ABORT_*} para detener la
 * cadena antes de que las reglas ejecutoras sean evaluadas.
 */
public interface Rule {

    /**
     * Número de prioridad de esta regla. Menor valor = mayor precedencia.
     * El orquestador ordena la lista por este valor en el constructor.
     */
    int priority();

    /** Nombre legible de la regla, usado en logs y en el Registro de Inacción. */
    String name();

    /**
     * Evalúa la regla contra el snapshot inmutable del sector.
     *
     * @param ctx snapshot del sector en el ciclo de evaluación actual (nunca null)
     * @return lista de acciones a ejecutar; nunca null, puede ser vacía
     */
    List<RuleAction> evaluate(RuleContext ctx);
}
