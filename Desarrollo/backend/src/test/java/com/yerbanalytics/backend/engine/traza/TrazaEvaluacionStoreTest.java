package com.yerbanalytics.backend.engine.traza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 3.5: la última traza por sector y por origen, segura entre el hilo MQTT y el scheduler. */
@DisplayName("TrazaEvaluacionStore")
class TrazaEvaluacionStoreTest {

    private static TrazaEvaluacion traza(String sector, OrigenEvaluacion origen, long epochMs) {
        return new TrazaEvaluacion(sector, "MZ-1", origen, Instant.ofEpochMilli(epochMs), "h", List.of());
    }

    private final TrazaEvaluacionStore store = new TrazaEvaluacionStore();

    @Test
    void sinEvaluacionesDevuelveVacio() {
        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA)).isEmpty();
        assertThat(store.masReciente("MZ-1-001")).isEmpty();
    }

    @Test
    void guardaLaUltimaPorSector() {
        store.guardar(traza("MZ-1-001", OrigenEvaluacion.TELEMETRIA, 1000));
        TrazaEvaluacion segunda = traza("MZ-1-001", OrigenEvaluacion.TELEMETRIA, 2000);
        store.guardar(segunda);
        store.guardar(traza("MZ-1-002", OrigenEvaluacion.TELEMETRIA, 500));

        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA)).containsSame(segunda);
        assertThat(store.ultima("MZ-1-002", OrigenEvaluacion.TELEMETRIA)).isPresent();
    }

    @Test
    void laTrazaDeBarridoNoPisaLaDeTelemetria() {
        TrazaEvaluacion tel = traza("MZ-1-001", OrigenEvaluacion.TELEMETRIA, 1000);
        TrazaEvaluacion bar = traza("MZ-1-001", OrigenEvaluacion.BARRIDO, 2000);
        store.guardar(tel);
        store.guardar(bar);

        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA)).containsSame(tel);
        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.BARRIDO)).containsSame(bar);
    }

    @Test
    void masRecienteDevuelveLaDeMayorMomentoSeaCualSeaElOrigen() {
        TrazaEvaluacion tel = traza("MZ-1-001", OrigenEvaluacion.TELEMETRIA, 3000);
        TrazaEvaluacion bar = traza("MZ-1-001", OrigenEvaluacion.BARRIDO, 2000);
        store.guardar(tel);
        store.guardar(bar);
        assertThat(store.masReciente("MZ-1-001")).containsSame(tel);

        TrazaEvaluacion barNuevo = traza("MZ-1-001", OrigenEvaluacion.BARRIDO, 4000);
        store.guardar(barNuevo);
        assertThat(store.masReciente("MZ-1-001")).containsSame(barNuevo);
    }

    @Test
    void masRecienteConUnSoloOrigenDevuelveEse() {
        TrazaEvaluacion bar = traza("MZ-1-001", OrigenEvaluacion.BARRIDO, 2000);
        store.guardar(bar);

        assertThat(store.masReciente("MZ-1-001")).containsSame(bar);
    }

    @Test
    void escriturasConcurrentesDeAmbosOrigenesNoPierdenNiRompenNada() throws Exception {
        int escrituras = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> trabajos = List.of(
                    pool.submit(() -> escribir(largada, OrigenEvaluacion.TELEMETRIA, escrituras)),
                    pool.submit(() -> escribir(largada, OrigenEvaluacion.BARRIDO, escrituras)));
            largada.countDown();
            for (Future<?> f : trabajos) {
                f.get(10, TimeUnit.SECONDS); // propaga cualquier excepción del hilo
            }
        } finally {
            pool.shutdownNow();
        }

        // Sobrevive la última de cada origen: ninguna escritura del otro origen la pisó.
        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA).orElseThrow().ts())
                .isEqualTo(Instant.ofEpochMilli(escrituras));
        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.BARRIDO).orElseThrow().ts())
                .isEqualTo(Instant.ofEpochMilli(escrituras));
    }

    private void escribir(CountDownLatch largada, OrigenEvaluacion origen, int n) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (int i = 1; i <= n; i++) {
            store.guardar(traza("MZ-1-001", origen, i));
        }
    }

    @Test
    void limpiar_descartaTodasLasTrazasDeTodosLosSectores() {
        store.guardar(traza("MZ-1-001", OrigenEvaluacion.TELEMETRIA, 1000));
        store.guardar(traza("MZ-1-002", OrigenEvaluacion.BARRIDO, 1000));

        store.limpiar();

        assertThat(store.ultima("MZ-1-001", OrigenEvaluacion.TELEMETRIA)).isEmpty();
        assertThat(store.masReciente("MZ-1-002")).isEmpty();
    }
}
