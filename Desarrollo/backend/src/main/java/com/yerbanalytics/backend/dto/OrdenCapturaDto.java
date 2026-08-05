package com.yerbanalytics.backend.dto;

/**
 * Orden entregada al dispositivo por el evento {@code orden} del stream SSE.
 * Espeja el schema {@code Orden} del contrato.
 */
public record OrdenCapturaDto(
        String ordenId,
        String sectorId,
        String zonaId,
        int posicionRiel,
        long emitidaEn,
        long venceEn,
        int intento
) {}
