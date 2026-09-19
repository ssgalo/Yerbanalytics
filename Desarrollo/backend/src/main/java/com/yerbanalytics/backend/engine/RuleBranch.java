package com.yerbanalytics.backend.engine;

/**
 * Define las ramas lógicas del motor de reglas.
 * Permite que reglas independientes se agrupen y no se bloqueen mutuamente
 * (ej: un bloqueo de riego no detiene la evaluación de la mediasombra).
 */
public enum RuleBranch {
    /** Rama principal: aplica a todo el sistema. Si se bloquea, se detiene todo. */
    GLOBAL,
    
    /** Rama del subsistema de riego (bombas y electroválvulas). */
    RIEGO,
    
    /** Rama del subsistema de insumos líquidos. */
    INSUMO,
    
    /** Rama del control ambiental (techo móvil / mediasombra). */
    MEDIASOMBRA,
    
    /** Rama para evaluación post-acción y latencia física. */
    SEGUIMIENTO
}
