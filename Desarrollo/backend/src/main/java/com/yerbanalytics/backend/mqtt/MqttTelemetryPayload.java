package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload de telemetría que publica un nodo ESP32. Espeja
 * {@code Desarrollo/embebido/comun/contrato.h} — ver {@link ContratoNodo} para las unidades.
 */
public record MqttTelemetryPayload(
    String mac,
    Integer battery,
    Integer signal,
    Long timestamp,
    MetricsPayload metrics
) {
    /**
     * Las 10 métricas modeladas.
     *
     * <p>{@code ignoreUnknown} es deliberado: el firmware publica además {@code salinidad} y
     * {@code tds}, que la sonda deriva por factor de la misma medición de EC y por eso no se
     * modelan. Sin esto la ingesta fallaría con el flag {@code ENVIAR_METRICAS_EXTENDIDAS}
     * activo, y cualquier clave que el firmware agregue en el futuro rompería la ingesta en
     * vez de ser ignorada.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetricsPayload(
        Double humSus,
        Double humAmb,
        Double temp,
        Double tempSuelo,
        /** % de luz (LDR), no índice UV. */
        Double uv,
        /** µS/cm — la ingesta convierte a dS/m. */
        Double ce,
        Double phSuelo,
        Double n,
        Double p,
        Double k
    ) {}
}
