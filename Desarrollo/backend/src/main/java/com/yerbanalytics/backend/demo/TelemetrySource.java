package com.yerbanalytics.backend.demo;

/** Qué telemetría lee el backend en modo demo. */
public enum TelemetrySource {
    /** El nodo ESP32 real: tópico {@code nursery/zone/{z}/telemetry}. */
    REAL_NODE,
    /** El simulador: tópico {@code nursery/demo/zone/{z}/telemetry}. */
    SIMULATOR
}
