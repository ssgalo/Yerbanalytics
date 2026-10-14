package com.yerbanalytics.backend.dto;

/**
 * Límites operativos que NO son umbrales de reglas y parámetros de seguimiento (HU-15). El tiempo
 * máximo de apertura de riego, el volumen diario de riego (se eliminó con los límites diarios) y la apertura máxima de la mediasombra se mudaron al catálogo de
 * parámetros de reglas ({@code GET/PUT /api/rules/parametros}).
 *
 * <p>{@code intervaloSensadoMinutos}: intervalo del pulso de sensado y evaluación (60–360 min).
 * {@code pasadaDiariaActiva} / {@code pasadaDiariaHora} ({@code HH:mm}): pasada diaria del riel; {@code null} en un
 * PUT conserva el valor guardado. {@code effectiveStaleThresholdSeconds} es de sólo lectura (lo calcula el
 * backend, se ignora en un PUT).
 */
public record ConfiguracionOperativa(
        double insumoDosisMax24hMl,
        int seguimientoLatenciaMin,
        double seguimientoDeltaMin,
        int intervaloSensadoMinutos,
        String updatedBy,
        Long updatedTs,
        Boolean pasadaDiariaActiva,
        String pasadaDiariaHora,
        Integer effectiveStaleThresholdSeconds
) {}
