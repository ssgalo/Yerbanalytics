package com.yerbanalytics.backend.readings;

/**
 * De dónde viene una lectura guardada en el historial. Decide qué cuenta como "lectura seguida": las reglas que
 * promedian o exigen N lecturas consecutivas miran sólo {@link #PULSE}, para que una lectura extra (manual, de
 * verificación, de arranque) no corra la media ni complete la cuenta.
 */
public enum ReadingOrigin {

    /** Respondió a un pulso de sensado, con o sin motor. */
    PULSE,

    /** Cualquier otra: envío manual, verificación de efectividad, secuencia LECTURA, arranque del nodo. */
    OTHER
}
