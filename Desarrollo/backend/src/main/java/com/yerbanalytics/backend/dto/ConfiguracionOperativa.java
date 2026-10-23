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
 *
 * <p>{@code mediasombraAbiertaDesde} / {@code mediasombraAbiertaHasta} ({@code HH:mm}): horario por defecto de la
 * mediasombra, el que rige en las macro-zonas sin plan de rustificación; {@code null} en un PUT conserva el guardado.
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
        Integer effectiveStaleThresholdSeconds,
        String mediasombraAbiertaDesde,
        String mediasombraAbiertaHasta
) {

    /** Sin horario de la mediasombra: en un PUT conserva el guardado. */
    public ConfiguracionOperativa(double insumoDosisMax24hMl, int seguimientoLatenciaMin, double seguimientoDeltaMin,
                                  int intervaloSensadoMinutos, String updatedBy, Long updatedTs,
                                  Boolean pasadaDiariaActiva, String pasadaDiariaHora,
                                  Integer effectiveStaleThresholdSeconds) {
        this(insumoDosisMax24hMl, seguimientoLatenciaMin, seguimientoDeltaMin, intervaloSensadoMinutos, updatedBy,
                updatedTs, pasadaDiariaActiva, pasadaDiariaHora, effectiveStaleThresholdSeconds, null, null);
    }
}
