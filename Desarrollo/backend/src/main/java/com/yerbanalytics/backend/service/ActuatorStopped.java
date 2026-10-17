package com.yerbanalytics.backend.service;

/**
 * Un actuador terminó (o lo cortaron) antes de lo que el backend creía: lo publica el seguimiento de comandos
 * al recibir el ack final del nodo, y lo escuchan quienes derivan el estado "regando" o "dosificando" por tiempo
 * ({@code DespachoRiego}, {@code SupplyDoseService}) para cerrarlo ya, sin esperar al plazo.
 *
 * @param zoneId   macro-zona del ack (sale del tópico)
 * @param sectorId sector del ack (sale del tópico)
 * @param actuator {@code valve} | {@code pump}, el del comando que quedó pendiente
 * @param commandId comando que el ack cita (el ORIGINAL: un {@code cancelado} cita el ON que se cortó), para no confundirlo con uno más nuevo
 * @param ackType  {@code detalle.tipo} del ack ({@code ok}, {@code cancelado}, un error del nodo…)
 */
public record ActuatorStopped(String zoneId, String sectorId, String actuator, String commandId, String ackType) {
}
