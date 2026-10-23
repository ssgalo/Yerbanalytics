package com.yerbanalytics.backend.engine.weather;

/**
 * En qué estado está el pronóstico (reglas_v2 §10, O-01…O-03). Lo único que se pierde sin internet es el pronóstico, y las reglas que
 * lo usan tienen un supuesto seguro para cuando falta.
 */
public enum ForecastStatus {

    /** Hay un pronóstico usable (fresco, o vencido hasta la vigencia utilizable del servicio): las reglas lo reciben. */
    AVAILABLE,
    /** Hubo uno, pero ya no es usable: las reglas actúan como si no hubiera pronóstico. */
    EXPIRED,
    /** Nunca hubo uno (el sistema arrancó sin conexión) o el modo demo «sin pronóstico» está activo. */
    ABSENT;

    /** {@code true} si las reglas deciden sin pronóstico, con sus supuestos conservadores. */
    public boolean isDegraded() {
        return this != AVAILABLE;
    }
}
