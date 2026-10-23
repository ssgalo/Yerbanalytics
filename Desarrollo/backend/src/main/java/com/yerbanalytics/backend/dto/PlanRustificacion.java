package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Plan de rustificación de una macro-zona. Espejo exacto del tipo {@code PlanRustificacion} del frontend.
 *
 * @param fechaInicio primer día del plan ({@code YYYY-MM-DD}, fecha local del vivero)
 * @param estado      {@code programado} (todavía no empezó) | {@code en_curso} (rige; pasada la última etapa sigue la
 *                    última) | {@code cancelado} (se eliminó)
 * @param creadoEn    epoch ms
 * @param canceladoEn epoch ms; {@code null} mientras rige
 */
public record PlanRustificacion(
        String id,
        String zonaId,
        String fechaInicio,
        List<EtapaRustificacion> etapas,
        String estado,
        String creadoPor,
        long creadoEn,
        Long canceladoEn
) {

    /** Etapa: {@code dias} días con la mediasombra abierta de {@code abiertaDesde} a {@code abiertaHasta} ({@code HH:mm}). */
    public record EtapaRustificacion(int orden, int dias, String abiertaDesde, String abiertaHasta) {
    }
}
