package com.yerbanalytics.backend.dto;

/** Estado de una orden para el seguimiento desde la plataforma (no es parte del contrato). */
public record EstadoOrden(
        String ordenId,
        String sectorId,
        String zonaId,
        int posicionRiel,
        String estado,
        int intentos,
        String motivoFallo,
        String detalleFallo,
        String capturaId,
        String imagenUrl,
        long creadaEn,
        Long entregadaEn,
        long venceEn
) {}
