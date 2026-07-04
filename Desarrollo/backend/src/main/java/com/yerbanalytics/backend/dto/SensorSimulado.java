package com.yerbanalytics.backend.dto;

/**
 * Sensor simulado del dashboard de simulación: un emisor de telemetría identificado por su
 * serial/MAC y asignado a una macro-zona. Vive en memoria del backend (se reinicia con la
 * app) y es <b>independiente</b> del registro de hardware: dar de alta un sensor simulado no
 * crea ningún dispositivo del sistema. El registro real es un paso aparte, con el mismo
 * serial/MAC. Espejo del tipo {@code SensorSimulado} del frontend.
 *
 * @param serial serial/MAC del sensor (identificador único entre los simulados)
 * @param zonaId macro-zona a la que emite (define el topic de publicación)
 */
public record SensorSimulado(
        String serial,
        String zonaId
) {}
