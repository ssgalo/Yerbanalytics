package com.yerbanalytics.backend.dto;

/**
 * Lectura manual de un sensor a enviar por MQTT (dashboard de simulación). Reproduce lo que
 * un nodo ESP32 publicaría. La fecha/hora ({@code timestamp}, epoch ms) es opcional: si es
 * null, el backend usa la hora actual. Espejo del tipo {@code EnvioTelemetria} del frontend.
 *
 * @param serial     serial/MAC del sensor (nodo testigo) — va como {@code mac} del payload
 * @param zonaId     macro-zona del sensor (define el topic de publicación)
 * @param battery    batería reportada (%), opcional
 * @param signal     señal reportada (dBm), opcional
 * @param timestamp  fecha/hora de la lectura (epoch ms), opcional
 * @param metrics    valores de las métricas; cada una es opcional pero debe venir al menos
 *                   una (permite enviar una sola métrica, p. ej. sólo radiación)
 */
public record EnvioTelemetria(
        String serial,
        String zonaId,
        Integer battery,
        Integer signal,
        Long timestamp,
        Metrics metrics
) {
    public record Metrics(
            Double humSus,
            Double humAmb,
            Double temp,
            Double ce,
            Double uv
    ) {}
}
