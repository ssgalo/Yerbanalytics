package com.yerbanalytics.backend.mqtt;

/**
 * Payload received from ESP32 via MQTT.
 */
public record MqttTelemetryPayload(
    String mac,
    Integer battery,
    Integer signal,
    Long timestamp,
    MetricsPayload metrics
) {
    public record MetricsPayload(
        Double humSus,
        Double humAmb,
        Double temp,
        Double ce,
        Double uv
    ) {}
}
