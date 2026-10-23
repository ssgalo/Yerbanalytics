package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Cuerpo de {@code POST /api/zonas/{zonaId}/rustificacion}: la fecha de inicio ({@code YYYY-MM-DD}) y las etapas, en
 * orden. El orden de cada etapa lo pone el backend. Espejo del tipo {@code NuevoPlanRustificacion} del frontend.
 */
public record NuevoPlanRustificacion(String fechaInicio, List<Etapa> etapas) {

    public record Etapa(Integer dias, String abiertaDesde, String abiertaHasta) {
    }
}
