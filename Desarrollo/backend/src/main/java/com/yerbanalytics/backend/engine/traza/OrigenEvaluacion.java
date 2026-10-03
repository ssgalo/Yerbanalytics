package com.yerbanalytics.backend.engine.traza;

/**
 * Qué disparó una evaluación. Se guarda una traza por origen: el barrido evalúa sin métricas
 * frescas y, si pisara a la de telemetría, el inspector nunca mostraría el caso que importa.
 */
public enum OrigenEvaluacion {
    TELEMETRIA,
    BARRIDO
}
