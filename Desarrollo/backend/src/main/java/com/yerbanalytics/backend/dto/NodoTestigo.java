package com.yerbanalytics.backend.dto;

/**
 * Estado del nodo sensor testigo que produce la lectura de una macro-zona.
 *
 * @param mac          serial/MAC del nodo, {@code null} si nunca reportó
 * @param battery      batería (%)
 * @param signal       señal WiFi (dBm RSSI)
 * @param bateriaBaja  batería por debajo del umbral configurado
 */
public record NodoTestigo(
        String mac,
        Integer battery,
        Integer signal,
        boolean bateriaBaja
) {}
