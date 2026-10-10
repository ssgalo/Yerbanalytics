package com.yerbanalytics.backend.dto;

/**
 * Límites operativos que NO son umbrales de reglas y parámetros de seguimiento (HU-15). El tiempo
 * máximo de apertura de riego, el volumen diario de riego (se eliminó con los límites diarios) y la apertura máxima de la mediasombra se mudaron al catálogo de
 * parámetros de reglas ({@code GET/PUT /api/rules/parametros}).
 *
 * <p>{@code sensingPullIntervalSeconds}: how often the backend asks every zone for a reading (10-3600 s);
 * {@code null} on PUT keeps the stored value. {@code effectiveStaleThresholdSeconds} is read-only
 * (computed by the backend, ignored on PUT).
 */
public record ConfiguracionOperativa(
        double insumoDosisMax24hMl,
        int seguimientoLatenciaMin,
        double seguimientoDeltaMin,
        int intervaloSensadoMinutos,
        int intervaloEvaluacionMinutos,
        String updatedBy,
        Long updatedTs,
        Integer sensingPullIntervalSeconds,
        Integer effectiveStaleThresholdSeconds
) {}
