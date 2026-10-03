package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.DetalleRiego;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 8.1: la cola de riego por macro-zona. */
@DisplayName("ColaRiego")
class ColaRiegoTest {

    private static SolicitudRiego sol(String zona, int n, double volumen) {
        return new SolicitudRiego(zona, zona + "-" + String.format("%03d", n), n,
                new DetalleRiego(volumen, (int) (volumen * 120), 40.0, false), "RiegoPorDeficitRule", Instant.EPOCH);
    }

    @Test
    void solicitarReemplazaLaSolicitudDelSector() {
        ColaRiego cola = new ColaRiego();

        cola.solicitar(sol("MZ-1", 1, 4.0));
        cola.solicitar(sol("MZ-1", 1, 5.0));

        assertThat(cola.pendientes("MZ-1")).hasSize(1);
        assertThat(cola.pendientes("MZ-1").get(0).detalle().volumenL()).isEqualTo(5.0);
    }

    @Test
    void retirarSacaLaSolicitudYNoFallaSiNoEsta() {
        ColaRiego cola = new ColaRiego();
        cola.solicitar(sol("MZ-1", 1, 4.0));
        cola.solicitar(sol("MZ-1", 2, 4.0));

        cola.retirar("MZ-1", "MZ-1-001");
        cola.retirar("MZ-1", "MZ-1-099");
        cola.retirar("MZ-9", "MZ-9-001");

        assertThat(cola.pendientes("MZ-1")).extracting(SolicitudRiego::sectorId).containsExactly("MZ-1-002");
        assertThat(cola.contiene("MZ-1-001")).isFalse();
        assertThat(cola.contiene("MZ-1-002")).isTrue();
    }

    @Test
    void pendientesSalenOrdenadasPorNumeroDeSectorNoPorOrdenDeLlegada() {
        ColaRiego cola = new ColaRiego();
        for (int n : new int[]{10, 2, 100, 1, 11, 3}) {
            cola.solicitar(sol("MZ-2", n, 4.0));
        }

        assertThat(cola.pendientes("MZ-2")).extracting(SolicitudRiego::numero)
                .containsExactly(1, 2, 3, 10, 11, 100);
    }

    @Test
    void laColaEsPorZona() {
        ColaRiego cola = new ColaRiego();
        cola.solicitar(sol("MZ-1", 1, 4.0));
        cola.solicitar(sol("MZ-2", 1, 4.0));

        assertThat(cola.zonas()).containsExactlyInAnyOrder("MZ-1", "MZ-2");
        assertThat(cola.pendientes("MZ-1")).hasSize(1);
        cola.retirar("MZ-1", "MZ-1-001");
        assertThat(cola.zonas()).containsExactly("MZ-2");
        assertThat(cola.pendientes("MZ-1")).isEmpty();
    }

    @Test
    void pendientesDevuelveUnaCopia() {
        ColaRiego cola = new ColaRiego();
        cola.solicitar(sol("MZ-1", 1, 4.0));

        List<SolicitudRiego> copia = cola.pendientes("MZ-1");
        cola.retirar("MZ-1", "MZ-1-001");

        assertThat(copia).hasSize(1);
    }

    @Test
    void accesoConcurrenteDesdeDosHilosNoPierdeSolicitudes() throws Exception {
        ColaRiego cola = new ColaRiego();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futuros = new ArrayList<>();
        for (int hilo = 0; hilo < 2; hilo++) {
            int base = hilo * 500;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 1; i <= 500; i++) {
                    cola.solicitar(sol("MZ-1", base + i, 4.0));
                }
                return null;
            }));
        }
        largada.countDown();
        for (var f : futuros) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(cola.pendientes("MZ-1")).hasSize(1000);
    }

    @Test
    void solicitarYRetirarEnParaleloSobreElMismoSectorDejaUnEstadoCoherente() throws Exception {
        ColaRiego cola = new ColaRiego();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        var productor = pool.submit(() -> {
            for (int i = 0; i < 5_000; i++) {
                cola.solicitar(sol("MZ-1", 1, 4.0));
            }
            return null;
        });
        var consumidor = pool.submit(() -> {
            for (int i = 0; i < 5_000; i++) {
                cola.pendientes("MZ-1");
                cola.retirar("MZ-1", "MZ-1-001");
                cola.contiene("MZ-1-001");
            }
            return null;
        });
        productor.get(20, TimeUnit.SECONDS);
        consumidor.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        // Sin excepciones y con a lo sumo una solicitud para el sector.
        assertThat(cola.pendientes("MZ-1").size()).isLessThanOrEqualTo(1);
        assertThat(cola.contiene("MZ-1-001")).isEqualTo(!cola.pendientes("MZ-1").isEmpty());
    }

    // ------------------------------------------------------------------ guarda: nunca una orden vacía

    private static SolicitudRiego conDetalle(double volumenL, int duracionSeg) {
        return new SolicitudRiego("MZ-1", "MZ-1-001", 1, new DetalleRiego(volumenL, duracionSeg, 40.0, false),
                "RiegoPorDeficitRule", Instant.EPOCH);
    }

    @Test
    void solicitarAceptaUnaOrdenValidaYLoDice() {
        ColaRiego cola = new ColaRiego();

        assertThat(cola.solicitar(conDetalle(5.0, 600))).isTrue();
        assertThat(cola.solicitar(conDetalle(10.0, 1200))).isTrue();     // el máximo del contrato entra
        assertThat(cola.solicitar(conDetalle(0.01, 1))).isTrue();        // y el mínimo
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0.0, 0", "5.0, 0", "5.0, -1", "5.0, 1201", "0.0, 600", "-1.0, 600",
            "NaN, 600", "Infinity, 600"})
    void solicitarRechazaDuracionFueraDeRangoOVolumenInvalido(double volumen, int duracion) {
        ColaRiego cola = new ColaRiego();

        assertThat(cola.solicitar(conDetalle(volumen, duracion))).isFalse();

        assertThat(cola.contiene("MZ-1-001")).isFalse();
        assertThat(cola.pendientes("MZ-1")).isEmpty();
    }

    @Test
    void unaOrdenInvalidaCancelaLaAnteriorDelSector() {
        // La última decisión manda: si la nueva no es despachable, la vieja ya no vale.
        ColaRiego cola = new ColaRiego();
        cola.solicitar(conDetalle(5.0, 600));

        assertThat(cola.solicitar(conDetalle(0.0, 0))).isFalse();

        assertThat(cola.contiene("MZ-1-001")).isFalse();
    }

    @Test
    void unaSolicitudSinDetalleSeRechaza() {
        ColaRiego cola = new ColaRiego();

        assertThat(cola.solicitar(new SolicitudRiego("MZ-1", "MZ-1-001", 1, null, "x", Instant.EPOCH))).isFalse();
    }

    // ------------------------------------------------------------------ carrera cancelación / despacho

    @Test
    void retirarSiCoincideSacaSoloLaMismaInstancia() {
        ColaRiego cola = new ColaRiego();
        SolicitudRiego vieja = conDetalle(4.0, 480);
        cola.solicitar(vieja);
        SolicitudRiego nueva = conDetalle(4.0, 480);   // igual por valor, otra instancia
        cola.solicitar(nueva);

        assertThat(cola.retirarSiCoincide(vieja)).isFalse();
        assertThat(cola.contiene("MZ-1-001")).isTrue();
        assertThat(cola.retirarSiCoincide(nueva)).isTrue();
        assertThat(cola.contiene("MZ-1-001")).isFalse();
        assertThat(cola.retirarSiCoincide(nueva)).isFalse();
    }

    @Test
    void reponerSiAusenteNoPisaUnaDecisionMasNueva() {
        ColaRiego cola = new ColaRiego();
        SolicitudRiego reclamada = conDetalle(4.0, 480);
        cola.solicitar(reclamada);
        cola.retirarSiCoincide(reclamada);

        cola.reponerSiAusente(reclamada);
        assertThat(cola.pendientes("MZ-1")).containsExactly(reclamada);

        SolicitudRiego otra = conDetalle(5.0, 600);
        cola.solicitar(otra);
        cola.reponerSiAusente(reclamada);
        assertThat(cola.pendientes("MZ-1")).containsExactly(otra);
    }

    @Test
    void esVigenteDiceSiSigueLaMismaInstancia() {
        ColaRiego cola = new ColaRiego();
        SolicitudRiego s = conDetalle(4.0, 480);
        cola.solicitar(s);

        assertThat(cola.esVigente(s)).isTrue();
        cola.retirar("MZ-1", "MZ-1-001");
        assertThat(cola.esVigente(s)).isFalse();
    }

    @Test
    void limpiarVaciaTodaLaCola() {
        ColaRiego cola = new ColaRiego();
        cola.solicitar(sol("MZ-1", 1, 4.0));
        cola.solicitar(sol("MZ-2", 1, 4.0));

        cola.limpiar();

        assertThat(cola.zonas()).isEmpty();
        assertThat(cola.contiene("MZ-1-001")).isFalse();
    }
}
