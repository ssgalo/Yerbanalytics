package com.yerbanalytics.backend.engine;

/**
 * Qué hardware tiene mapeado (registrado) un sector y su macro-zona, vista que el motor arma <b>una vez por zona</b>
 * para que las reglas no consulten la base (S-04, HU-18 CA-04). Habla de lo registrado, no de lo sano: que un
 * dispositivo esté averiado es de S-05.
 *
 * @param valve    el sector tiene su electroválvula
 * @param pump     el sector tiene su bomba peristáltica
 * @param zoneNode la macro-zona tiene su nodo testigo (sin él no hay lectura con que regar)
 * @param zoneShade la macro-zona tiene al menos una mediasombra, en cualquiera de sus sectores (el hardware las
 *                  registra por sector; la v2 exige una por macro-zona)
 */
public record HardwareCoverage(boolean valve, boolean pump, boolean zoneNode, boolean zoneShade) {

    /** Todo mapeado. Es lo que ve un contexto que no arma la cobertura (los tests de reglas sueltas). */
    public static final HardwareCoverage COMPLETE = new HardwareCoverage(true, true, true, true);

    /** Nada mapeado: un sector sin ningún dispositivo registrado. */
    public static final HardwareCoverage NONE = new HardwareCoverage(false, false, false, false);

    /** El riego necesita la electroválvula del sector y el nodo testigo que mide la humedad de su zona. */
    public boolean canIrrigate() {
        return valve && zoneNode;
    }

    /** La dosificación necesita la bomba del sector. */
    public boolean canDose() {
        return pump;
    }

    /** La mediasombra se exige a la macro-zona, no al sector. */
    public boolean canShade() {
        return zoneShade;
    }
}
