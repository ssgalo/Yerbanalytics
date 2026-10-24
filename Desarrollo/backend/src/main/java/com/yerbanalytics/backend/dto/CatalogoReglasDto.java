package com.yerbanalytics.backend.dto;

import java.util.List;

/** Respuesta de {@code GET/PUT /api/rules/parametros}: las piezas y una entrada por copia {@code (regla, clave)}. */
public record CatalogoReglasDto(
        List<ReglaCatalogoDto> reglas,
        List<ParametroDto> parametros
) {}
