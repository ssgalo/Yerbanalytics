package com.yerbanalytics.backend.dto;

import java.util.List;

/** Respuesta de {@code GET/PUT /api/rules/parametros}: catálogo normalizado (cada parámetro una vez). */
public record CatalogoReglasDto(
        List<ReglaCatalogoDto> reglas,
        List<ParametroDto> parametros
) {}
