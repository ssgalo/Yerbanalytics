package com.yerbanalytics.backend.engine.traza;

/**
 * Qué disparó una evaluación. Desde que el motor sólo decide en el pulso de sensado hay un único origen: la
 * telemetría que responde a un pulso con motor. Se conserva el enum porque la traza y la API lo exponen.
 */
public enum OrigenEvaluacion {
    TELEMETRIA
}
