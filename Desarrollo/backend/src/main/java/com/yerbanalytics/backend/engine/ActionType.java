package com.yerbanalytics.backend.engine;

/**
 * Tipos de acción que una {@link Rule} puede emitir.
 *
 * <ul>
 *   <li><b>Ejecutoras (prioridad alta ≥ 10):</b> producen efectos reales sobre actuadores.</li>
 *   <li><b>Bloqueantes (prioridad baja 0–5):</b> cancelan la cadena de evaluación.</li>
 *   <li><b>Informativas:</b> no producen efecto físico, solo se persisten en el historial
 *       como Registro de Inacción (por qué el sistema decidió no actuar).</li>
 * </ul>
 */
public enum ActionType {

    // --- Ejecutoras ---
    /** Abrir la electroválvula de riego del sector. */
    ACTIVAR_VALVULA,

    /** Activar la bomba peristáltica de insumo del sector. */
    ACTIVAR_BOMBA,

    // --- Bloqueantes ---
    /** Abortar el riego (la evaluación de reglas ejecutoras de riego se detiene). */
    ABORT_RIEGO,

    /** Abortar la dosificación de insumo. */
    ABORT_INSUMO,

    /**
     * Abortar toda actuación del ciclo para este sector.
     * Ninguna regla ejecutora posterior será evaluada.
     */
    ABORT_ALL,

    // --- Informativas ---
    /**
     * No se ejecuta ninguna acción, pero se registra en el historial el motivo
     * (Registro de Inacción). Permite que el usuario sepa por qué el sistema
     * decidió deliberadamente no actuar.
     */
    NOOP_INFO;

    /** @return true si esta acción debe detener la cadena de evaluación. */
    public boolean isBlocking() {
        return this == ABORT_RIEGO || this == ABORT_INSUMO || this == ABORT_ALL;
    }
}
