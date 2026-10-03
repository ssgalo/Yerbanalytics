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
}
