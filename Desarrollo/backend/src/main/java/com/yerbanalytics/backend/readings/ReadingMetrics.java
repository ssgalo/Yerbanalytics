package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload.MetricsPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Las métricas que trae una lectura, por su clave del contrato ({@code humSus}, {@code temp}…) y en las unidades
 * canónicas de la plataforma. Sólo están las que el nodo mandó: una lectura parcial (un sensor en falla, o un envío
 * manual de una sola métrica) no inventa las demás.
 *
 * <p>Es el único lugar donde el payload cambia de unidad: la conductividad llega en µS/cm y se guarda en dS/m
 * (ver {@link ContratoNodo}); la validación de rango y la escritura en la zona ven ya el valor convertido.
 * Inmutable.
 */
public final class ReadingMetrics {

    private final Map<String, Double> values;

    private ReadingMetrics(Map<String, Double> values) {
        this.values = Map.copyOf(values);
    }

    /** Lectura con exactamente estos valores, ya en unidades canónicas. */
    public static ReadingMetrics of(Map<String, Double> values) {
        return new ReadingMetrics(values);
    }

    /** Las métricas presentes del payload, con la CE convertida a dS/m. {@code null} da una lectura vacía. */
    public static ReadingMetrics from(MetricsPayload pm) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (pm != null) {
            put(out, "humSus", pm.humSus());
            put(out, "humAmb", pm.humAmb());
            put(out, "temp", pm.temp());
            put(out, "tempSuelo", pm.tempSuelo());
            put(out, "uv", pm.uv());
            put(out, "ce", ContratoNodo.ceADsPorM(pm.ce()));
            put(out, "phSuelo", pm.phSuelo());
            put(out, "n", pm.n());
            put(out, "p", pm.p());
            put(out, "k", pm.k());
        }
        return new ReadingMetrics(out);
    }

    private static void put(Map<String, Double> out, String key, Double value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** El valor de la métrica, o {@code null} si la lectura no la trae. */
    public Double get(String key) {
        return values.get(key);
    }

    /** Esta misma lectura sin las métricas dadas; no modifica la original. */
    public ReadingMetrics without(Set<String> keys) {
        Map<String, Double> rest = new LinkedHashMap<>(values);
        rest.keySet().removeAll(keys);
        return new ReadingMetrics(rest);
    }

    /** Cada métrica con su valor, para recorrerlas. */
    public Map<String, Double> asMap() {
        return values;
    }
}
