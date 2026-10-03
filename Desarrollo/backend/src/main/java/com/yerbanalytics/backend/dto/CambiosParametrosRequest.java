package com.yerbanalytics.backend.dto;

import com.yerbanalytics.backend.engine.parametros.CambioParametro;

import java.util.List;

/** Cuerpo de {@code PUT /api/rules/parametros}: lote de cambios, todo o nada. */
public record CambiosParametrosRequest(List<CambioParametro> cambios) {}
