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

    /** Nombre técnico de la regla, usado en logs y en el Registro de Inacción. */
    String name();

    /**
     * Etiqueta legible para el usuario final. Se usa en el Inspector de Decisiones
     * (visualización del DAG) y en cualquier UI orientada al operador del vivero.
     *
     * <p>Por defecto retorna {@link #name()} para mantener retrocompatibilidad.
     * Cada regla DEBE sobreescribir este método con una descripción comprensible,
     * sin tecnicismos: el usuario es un agrónomo u operador, no un desarrollador.
     */
    default String label() {
        return name();
    }

    /**
     * Rama o subsistema al que pertenece esta regla.
     * Permite ejecución independiente en forma de DAG.
     */
    default RuleBranch branch() {
        return RuleBranch.GLOBAL;
    }

    /**
     * Evalúa la regla contra el snapshot inmutable del sector.
     *
     * @param ctx snapshot del sector en el ciclo de evaluación actual (nunca null)
     * @return lista de acciones a ejecutar; nunca null, puede ser vacía
     */
    List<RuleAction> evaluate(RuleContext ctx);
}
