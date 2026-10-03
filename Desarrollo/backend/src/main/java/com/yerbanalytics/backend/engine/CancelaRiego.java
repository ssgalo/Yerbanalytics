package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.SolicitudRiego;

/**
 * Marca de un {@code ABORT_RIEGO} (o {@code ABORT_ALL}) que es una <b>cancelación explícita de seguridad</b>:
 * además de cortar la rama en esta evaluación, retira de la cola la solicitud de riego del sector.
 *
 * <p>Un {@code ABORT_RIEGO} sin esta marca (la regla de ciclo, el tope de R-02, R-03) sólo corta la evaluación:
 * "la regla ya no pide riego" no cancela una ronda ya decidida. El nodo testigo mide UN sector; cuando el
 * despacho lo riega la humedad sube, R-01 y R-02 dejan de aplicar, y si eso retirara lo pendiente los demás
 * sectores de la macro-zona quedarían sin agua.
 *
 * @param soloDeficitComun {@code true}: sólo retira solicitudes de R-01 (la ventana horaria y la pausa por
 *                         aplicación frenan el riego común, no el déficit crítico); {@code false}: cualquiera
 */
public record CancelaRiego(boolean soloDeficitComun) implements DetalleAccion {

    /** Retira cualquier solicitud del sector: sustrato saturado, bloqueo manual, sensor sin datos. */
    public static final CancelaRiego TODAS = new CancelaRiego(false);

    /** Retira sólo las de R-01: ventana cerrada (R-05) y pausa por aplicación (R-06). */
    public static final CancelaRiego SOLO_DEFICIT_COMUN = new CancelaRiego(true);

    /** {@code true} si esta cancelación alcanza a la solicitud dada. */
    public boolean alcanza(SolicitudRiego s) {
        return !soloDeficitComun || !s.esDeficitCritico();
    }
}
