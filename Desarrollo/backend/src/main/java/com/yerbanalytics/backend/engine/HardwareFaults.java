package com.yerbanalytics.backend.engine;

/**
 * Qué actuadores de un sector y su macro-zona están marcados como averiados (S-05), la vista que el motor arma
 * <b>una vez por zona</b> para que las reglas no consulten la base. Es el complemento de {@link HardwareCoverage}: aquella
 * habla de lo registrado, ésta de lo sano. Una falla sólo se levanta registrando la reparación.
 *
 * @param hydraulic falla de la electroválvula del sector (el {@code tipo} del error del nodo que la marcó, p. ej.
 *                  {@code SIN_CAUDAL}); {@code null} si está sana
 * @param shade     falla de la mediasombra de la macro-zona (p. ej. {@code MEDIASOMBRA_FIN_DE_CARRERA}); {@code null} si
 *                  está sana. La malla es de la zona: todos sus sectores ven lo mismo
 */
public record HardwareFaults(String hydraulic, String shade) {

    /** Nada averiado. Es lo que ve un contexto que no arma las fallas (los tests de reglas sueltas). */
    public static final HardwareFaults NONE = new HardwareFaults(null, null);

    /** El riego y la dosificación del sector están fuera de lo autónomo. */
    public boolean hasHydraulic() {
        return hydraulic != null && !hydraulic.isBlank();
    }

    /** La malla de la macro-zona no debe recibir comandos. */
    public boolean hasShade() {
        return shade != null && !shade.isBlank();
    }
}
