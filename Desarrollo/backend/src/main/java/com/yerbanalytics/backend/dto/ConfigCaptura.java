package com.yerbanalytics.backend.dto;

/**
 * Configuracion de captura que el backend le entrega al dispositivo.
 * Espeja el schema {@code ConfigCaptura} del contrato (contratos/camara/v1/openapi.yaml).
 *
 * <p>La resolucion y la calidad son propiedad del backend, no del cliente: el dispositivo
 * esta montado en un riel y cambiarle el codigo para ajustar un parametro no es practicable.
 */
public record ConfigCaptura(
        int anchoMax,
        int altoMax,
        double calidadJpeg,
        int warmupMs,
        int heartbeatSeg,
        int timeoutOrdenSeg,
        int maxColaOrdenes
) {}
