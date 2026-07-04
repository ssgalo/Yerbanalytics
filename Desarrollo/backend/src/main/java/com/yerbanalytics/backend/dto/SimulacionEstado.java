package com.yerbanalytics.backend.dto;

/**
 * Estado del modo de operación del vivero (herramienta de simulación). Espejo del tipo
 * {@code SimulacionEstado} del frontend.
 *
 * @param modo           modo vigente: {@code "estatico"} o {@code "simulacion"}
 * @param autoSimulador  si el simulador automático de telemetría está activo
 */
public record SimulacionEstado(
        String modo,
        boolean autoSimulador
) {}
