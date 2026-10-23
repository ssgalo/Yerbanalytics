package com.yerbanalytics.backend.dto;

/**
 * Lo operativo que NO es un umbral de regla (HU-15). El tiempo máximo de apertura de riego, el volumen diario, la
 * apertura de la mediasombra y el seguimiento post-acción ({@code seguimiento.*}) están en el catálogo de parámetros de
 * reglas ({@code GET/PUT /api/rules/parametros}); {@code insumoDosisMax24hMl} se borró (nadie decidía con él). Un PUT de
 * un cliente viejo que todavía los manda no falla: se ignoran.
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
    public ConfiguracionOperativa(int intervaloSensadoMinutos, String updatedBy, Long updatedTs,
                                  Boolean pasadaDiariaActiva, String pasadaDiariaHora,
                                  Integer effectiveStaleThresholdSeconds) {
        this(intervaloSensadoMinutos, updatedBy, updatedTs, pasadaDiariaActiva, pasadaDiariaHora,
                effectiveStaleThresholdSeconds, null, null);
    }
}
