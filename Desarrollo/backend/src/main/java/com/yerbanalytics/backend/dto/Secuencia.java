package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Foto inmutable de una secuencia guionada (design add-secuencias-demo-expo §2.7). {@code tipo}:
 * RIEGO | MEDIASOMBRA | LECTURA. {@code estado}: EN_CURSO | COMPLETADA | FALLIDA | CANCELADA.
 * {@code error} es el detalle del primer paso fallido. {@code sectorId} es {@code null} en la lectura
 * (el sensado es por zona) y {@code lectura} lo es hasta que llega la telemetría.
 */
public record Secuencia(
        String id,
        String tipo,
        String estado,
        String zonaId,
        String sectorId,
        ParametrosSecuencia parametros,
        long iniciadaEn,
        Long finalizadaEn,
        boolean cancelacionSolicitada,
        String error,
        LecturaSecuencia lectura,
        List<PasoSecuencia> pasos
) {}
