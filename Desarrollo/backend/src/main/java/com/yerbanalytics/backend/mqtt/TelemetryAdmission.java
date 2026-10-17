package com.yerbanalytics.backend.mqtt;

/**
 * Vista neutra, de sólo lectura, que consultan los receptores de telemetría para saber si admiten un mensaje según su
 * tópico. Es el mismo recurso que {@code RuleOverrides}: el código productivo depende de esta interfaz y no de nada de
 * demo; sin modo demo no hay ninguna implementación y rige {@link #ALL}.
 */
public interface TelemetryAdmission {

    /** Producción: se admite todo lo que llegue (sólo está suscripto el tópico real). */
    TelemetryAdmission ALL = topic -> true;

    /** Si la telemetría recibida en {@code topic} debe procesarse. */
    boolean admits(String topic);

    /**
     * Id de zona de un tópico de telemetría: el segmento previo al último, igual para
     * {@code nursery/zone/{z}/telemetry} que para la variante de demo. {@code null} si no tiene forma de tópico de zona.
     */
    static String zoneId(String topic) {
        if (topic == null) {
            return null;
        }
        String[] parts = topic.split("/");
        return parts.length >= 4 ? parts[parts.length - 2] : null;
    }
}
