package com.yerbanalytics.backend.dto;

/**
 * Un paso de una secuencia (design add-secuencias-demo-expo §2.7). {@code tipo}: ABRIR | ESPERAR |
 * CERRAR | DESPLEGAR | ENROLLAR | PEDIR | ESPERAR_TELEMETRIA | MOSTRAR. {@code estado}: PENDIENTE |
 * EN_CURSO | OK | ERROR | OMITIDO. {@code esperaHasta} sólo en los pasos ESPERAR. Todas las claves
 * viajan siempre, con {@code null} si no aplican.
 */
public record PasoSecuencia(
        int n,
        String tipo,
        String estado,
        String codigoError,
        String detalle,
        String commandId,
        Long esperaHasta,
        Long iniciadoEn,
        Long terminadoEn
) {}
