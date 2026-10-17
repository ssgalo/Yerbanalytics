package com.yerbanalytics.backend.demo;

/**
 * Tópicos de demo. Árbol aparte de {@code nursery/zone/...}: el comodín productivo {@code nursery/zone/+/telemetry} no lo
 * alcanza. Mismo payload y unidades que el firmware. Espejo en {@code simulador/server/contract.ts}.
 */
public final class DemoTelemetryTopics {

    /** Prefijo común: todo lo que cuelga de acá es de demo. */
    public static final String PREFIX = "nursery/demo/";

    /** Suscripción a la telemetría simulada de todas las zonas. */
    public static final String TELEMETRY_SUBSCRIPTION = PREFIX + "zone/+/telemetry";

    private DemoTelemetryTopics() {
    }
}
