package com.yerbanalytics.backend.mqtt;

/**
 * Un error que informó el nodo (S-05), ya validado contra el contrato: el tipo es uno de {@link NodeErrorType}.
 * Viene de {@code nursery/zone/{zona}/errores}, o del ack {@code falla_mecanica}, que es el mismo hecho de la
 * malla visto por otro camino (en ese caso no hay {@code node} ni {@code value}). El {@code timestamp} del payload no se
 * guarda: el reloj del nodo puede estar sin NTP, así que la falla se fecha con la hora de recepción.
 *
 * @param zoneId      la zona sale del tópico, no del payload
 * @param node        serial/MAC del nodo que informa; {@code null} si no se sabe
 * @param sectorId    sector del error de caudal; vacío o {@code null} en el de la malla (es de la macro-zona)
 * @param type        qué falló
 * @param value       caudal medido en L/h (como texto) en los de caudal; {@code CERRADA} o {@code ABIERTA} en el de la
 *                    malla (qué final de carrera falló); {@code null} si no vino
 */
public record NodeError(String zoneId, String node, String sectorId, NodeErrorType type, String value) {
}
