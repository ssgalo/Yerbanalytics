package com.yerbanalytics.backend.engine;

/**
 * Qué actuadores de un sector y su macro-zona están marcados como averiados (S-05), la vista que el motor arma
 * <b>una vez por zona</b> para que las reglas no consulten la base. Es el complemento de {@link HardwareCoverage}: aquella
 * habla de lo registrado, ésta de lo sano. Una falla sólo se levanta registrando la reparación.
 *
 * @param hydraulic falla de la electroválvula del sector (el {@code tipo} del error del nodo que la marcó, p. ej.
 *                  {@code SIN_CAUDAL}); {@code null} si está sana
 *
 * <p>La falla de la mediasombra no está acá: la malla es de la macro-zona ({@link MallaZona}).
 */
public record HardwareFaults(String hydraulic) {

    /** Nada averiado. Es lo que ve un contexto que no arma las fallas (los tests de reglas sueltas). */
    public static final HardwareFaults NONE = new HardwareFaults(null);

    /** El riego y la dosificación del sector están fuera de lo autónomo. */
    public boolean hasHydraulic() {
        return hydraulic != null && !hydraulic.isBlank();
    }
}
