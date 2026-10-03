package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.DetalleRiego;

import java.time.Instant;

/**
 * Pedido de riego de un sector, en la cola de su macro-zona hasta que el despacho lo abre.
 *
 * <p>Lleva sólo datos planos (no la entidad del sector): la solicitud la crea el hilo de la
 * telemetría y la consume el del despacho, con otra sesión de base de datos de por medio.
 *
 * @param zonaId      macro-zona del sector
 * @param sectorId    identificador del sector
 * @param numero      numeración del sector dentro de la zona: el orden de despacho
 * @param detalle     volumen y duración calculados por la regla
 * @param regla       regla que lo ordenó (para el historial)
 * @param solicitadaEn instante de la decisión
 */
public record SolicitudRiego(
        String zonaId,
        String sectorId,
        int numero,
        DetalleRiego detalle,
        String regla,
        Instant solicitadaEn
) {

    /** Nombre de R-02 (déficit crítico): lo que se despacha de noche y no depende de la ventana ni de la pausa. */
    public static final String REGLA_DEFICIT_CRITICO = "DeficitCriticoRule";

    /** Nombre de R-01 (déficit común). */
    public static final String REGLA_DEFICIT_COMUN = "RiegoPorDeficitRule";

    /** {@code true} si la ordenó R-02. Toda otra regla se trata como riego común (la más restringida). */
    public boolean esDeficitCritico() {
        return REGLA_DEFICIT_CRITICO.equals(regla);
    }
}
