package com.yerbanalytics.backend.engine;

/**
 * La mediasombra de una macro-zona, tal como la registra el hardware: una por zona, como el nodo testigo
 * (add-rustificacion-por-macrozona). Habla de lo registrado (S-04) y de lo sano (S-05).
 *
 * @param registrada la zona tiene su mediasombra registrada
 * @param falla      el {@code tipo} de la falla marcada (p. ej. {@code MEDIASOMBRA_FIN_DE_CARRERA}); {@code null} si está sana
 */
public record MallaZona(boolean registrada, String falla) {

    /** Registrada y sana. Es lo que ve un contexto que no la arma (los tests de reglas sueltas). */
    public static final MallaZona OPERATIVA = new MallaZona(true, null);

    /** La malla no debe recibir comandos hasta que alguien registre la reparación. */
    public boolean tieneFalla() {
        return falla != null && !falla.isBlank();
    }

    /**
     * Por qué no se le puede mandar un comando a la malla, o {@code null} si se puede: bloqueo manual de la zona (S-01),
     * sin mediasombra registrada (S-04) o con falla sin reparar (S-05).
     */
    public String motivoSinComando(boolean zonaBloqueada) {
        if (zonaBloqueada) {
            return "bloqueo manual vigente en la macro-zona";
        }
        if (!registrada) {
            return "la macro-zona no tiene mediasombra registrada";
        }
        if (tieneFalla()) {
            return "falla mecánica sin reparar en la mediasombra";
        }
        return null;
    }
}
