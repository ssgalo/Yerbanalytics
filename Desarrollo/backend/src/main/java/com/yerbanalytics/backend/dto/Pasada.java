package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Foto inmutable de una pasada del riel (design §2.6). {@code estado}: EN_CURSO | COMPLETADA |
 * FALLIDA | CANCELADA. {@code error} es el detalle del primer paso fallido.
 */
public record Pasada(
        String id,
        String estado,
        long iniciadaEn,
        Long finalizadaEn,
        boolean cancelacionSolicitada,
        String error,
        List<PasoPasada> pasos
) {}
