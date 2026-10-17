package com.yerbanalytics.backend.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DemoTelemetrySourceStore")
class DemoTelemetrySourceStoreTest {

    private static final String REAL = "nursery/zone/MZ-1/telemetry";
    private static final String DEMO = "nursery/demo/zone/MZ-1/telemetry";

    private final DemoTelemetrySourceStore store = new DemoTelemetrySourceStore();

    @Test
    void arrancaConElNodoReal() {
        assertThat(store.get()).isEqualTo(TelemetrySource.REAL_NODE);
        assertThat(store.admits(REAL)).isTrue();
        assertThat(store.admits(DEMO)).isFalse();
    }

    @Test
    void conElSimuladorSoloAdmiteElTopicoDeDemo() {
        store.set(TelemetrySource.SIMULATOR);

        assertThat(store.admits(DEMO)).isTrue();
        assertThat(store.admits(REAL)).isFalse();
    }

    @Test
    void volverAlNodoRealRestableceLaCompuerta() {
        store.set(TelemetrySource.SIMULATOR);
        store.set(TelemetrySource.REAL_NODE);

        assertThat(store.admits(REAL)).isTrue();
        assertThat(store.admits(DEMO)).isFalse();
    }

    @Test
    void unTopicoNuloNoSeAdmite() {
        assertThat(store.admits(null)).isFalse();
    }
}
