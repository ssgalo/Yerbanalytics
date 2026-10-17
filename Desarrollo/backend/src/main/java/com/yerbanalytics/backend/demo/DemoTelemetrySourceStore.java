package com.yerbanalytics.backend.demo;

import com.yerbanalytics.backend.mqtt.TelemetryAdmission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fuente de telemetría elegida, en memoria (un reinicio vuelve a {@link TelemetrySource#REAL_NODE}). Sólo existe con
 * {@code yerbanalytics.demo.enabled=true}. Es la compuerta de los receptores: con el simulador sólo pasa el tópico de demo;
 * con el nodo real, sólo el tópico real.
 */
@Component
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoTelemetrySourceStore implements TelemetryAdmission {

    private volatile TelemetrySource source = TelemetrySource.REAL_NODE;

    public TelemetrySource get() {
        return source;
    }

    public void set(TelemetrySource source) {
        this.source = source;
    }

    @Override
    public boolean admits(String topic) {
        if (topic == null) {
            return false;
        }
        boolean fromDemo = topic.startsWith(DemoTelemetryTopics.PREFIX);
        return fromDemo == (source == TelemetrySource.SIMULATOR);
    }
}
