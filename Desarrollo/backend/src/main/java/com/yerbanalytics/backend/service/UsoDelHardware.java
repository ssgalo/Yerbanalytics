package com.yerbanalytics.backend.service;

import java.util.Optional;

/**
 * Algo que comanda el único ESP32 del vivero (la pasada del riel, las secuencias de actuadores).
 * {@link GuardiaHardware} le pregunta a cada uno si está en uso antes de dejar arrancar a otro.
 */
public interface UsoDelHardware {

    /**
     * El motivo, legible, por el que el hardware está ocupado (es el mensaje del 409), o vacío si
     * está libre. Se deriva de la foto {@code volatile} del servicio: no toma locks.
     */
    Optional<String> ocupadoPor();
}
