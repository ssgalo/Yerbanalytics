package com.yerbanalytics.backend.dto;

/**
 * Límites operativos que NO son umbrales de reglas y parámetros de seguimiento (HU-15). El tiempo
 * máximo de apertura de riego y la apertura máxima de la mediasombra se mudaron al catálogo de
 * parámetros de reglas ({@code GET/PUT /api/rules/parametros}).
 */
public record ConfiguracionOperativa(
        double riegoVolMaxDiarioMl,
        double insumoDosisMax24hMl,
        int seguimientoLatenciaMin,
        double seguimientoDeltaMin,
        int intervaloSensadoMinutos,
        int intervaloEvaluacionMinutos,
        String updatedBy,
        Long updatedTs
) {}
