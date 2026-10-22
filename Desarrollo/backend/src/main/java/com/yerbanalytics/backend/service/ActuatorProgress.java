package com.yerbanalytics.backend.service;

/**
 * El nodo avisó cómo va un comando de carga (ACK intermedio {@code EN_CURSO}, add-cola-actuadores-nodo): lo publica el
 * seguimiento de comandos y lo escuchan quienes derivan "En cola" / "Regando" / "Dosificando"
 * ({@code DespachoRiego}, {@code SupplyDoseService}). Es un evento de Spring, como {@link ActuatorStopped}: así el
 * seguimiento no depende de ellos (evita el ciclo despacho, publisher, seguimiento).
 *
 * @param zoneId    macro-zona del ack (sale del tópico)
 * @param sectorId  sector del ack (sale del tópico)
 * @param actuator  {@code valve} | {@code pump} | {@code shade}, el del comando pendiente
 * @param commandId comando al que se refiere
 * @param phase     {@code QUEUED} (el nodo lo tiene en cola) | {@code STARTED} (arrancó de verdad)
 * @param atMs      hora de RECEPCIÓN en el backend (el ack no lleva hora y el reloj del nodo puede no estar sincronizado)
 * @param maxWaitMs en {@code QUEUED}, cota superior de lo que falta para que arranque; 0 si no la informó o en STARTED
 */
public record ActuatorProgress(String zoneId, String sectorId, String actuator, String commandId, Phase phase,
                               long atMs, long maxWaitMs) {

    public enum Phase {
        QUEUED,
        STARTED
    }
}
