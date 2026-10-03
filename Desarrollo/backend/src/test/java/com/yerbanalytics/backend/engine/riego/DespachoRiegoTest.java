package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tareas 8.3 a 8.6: el despacho por tandas. Reloj mutable, repositorios y publicador falsos; la cola es real.
 */
@DisplayName("DespachoRiego")
class DespachoRiegoTest {

    private static final Instant T0 = Instant.parse("2026-10-03T13:05:00Z");   // 10:05 locales

    private RelojDePrueba reloj;
    private ColaRiego cola;
    private ComandoActuadorPublisher publisher;
    private HistorialService historial;
    private HistorialRepository historialRepository;
    private SectorRepository sectorRepository;
    private ManualLockRepository bloqueos;
    private CatalogoParametrosService parametros;
    private DespachoRiego despacho;
    /** begin / commit / rollback de las transacciones y "publish" de cada comando, en orden. */
    private List<String> eventos;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(T0);
        cola = new ColaRiego();
        eventos = Collections.synchronizedList(new ArrayList<>());
        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any())).thenAnswer(i -> {
            eventos.add("publish");
            return new ComandoActuadorPublisher.Resultado(true, "c", null);
        });
        historial = mock(HistorialService.class);
        historialRepository = mock(HistorialRepository.class);
        when(historialRepository.riegosDesde(anyLong())).thenReturn(List.of());
        sectorRepository = mock(SectorRepository.class);
        when(sectorRepository.findById(anyString())).thenAnswer(i -> Optional.of(sector(i.getArgument(0))));
        bloqueos = mock(ManualLockRepository.class);
        when(bloqueos.findByActiveTrue()).thenReturn(List.of());
        parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        despacho = nuevoDespacho();
    }

    private DespachoRiego nuevoDespacho() {
        return new DespachoRiego(cola, publisher, historial, historialRepository, sectorRepository, bloqueos,
                parametros, reloj, new TxFalso(eventos));
    }

    private static String zonaDe(String sectorId) {
        return sectorId.substring(0, sectorId.lastIndexOf('-'));
    }

    private static SectorEntity sector(String id) {
        SectorEntity s = RuleContextTestFactory.sectorBasico(id);
        s.setN(Integer.parseInt(id.substring(id.lastIndexOf('-') + 1)));
        s.setZona(RuleContextTestFactory.zonaBasica(zonaDe(id)));
        return s;
    }

    private static String id(String zona, int n) {
        return zona + "-" + String.format("%03d", n);
    }

    private void pedir(String zona, int n, int duracionSeg) {
        cola.solicitar(new SolicitudRiego(zona, id(zona, n), n,
                new DetalleRiego(5.0, duracionSeg, 40.0, false), "RiegoPorDeficitRule", reloj.instant()));
    }

    private void pedirZona(String zona, int cantidad, int duracionSeg) {
        // A propósito en orden inverso: el despacho ordena, no la llegada.
        for (int n = cantidad; n >= 1; n--) {
            pedir(zona, n, duracionSeg);
        }
    }

    private List<String> sectoresPublicados() {
        ArgumentCaptor<String> sectores = ArgumentCaptor.forClass(String.class);
        verify(publisher, atLeastOnce()).publicar(any(), sectores.capture(), eq("valve"), eq("ON"), any());
        return sectores.getAllValues();
    }

    private void usarSimultaneos(int n) {
        when(parametros.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosRiego.SECTORES_SIMULTANEOS, String.valueOf(n))));
    }

    // ------------------------------------------------------------------ 8.3

    @Test
    void cienPendientesEnMZ2_publicaLosPrimeros10EnOrdenYRegistraCadaRiego() {
        pedirZona("MZ-2", 100, 600);

        despacho.tick();

        assertThat(sectoresPublicados()).containsExactly(
                "MZ-2-001", "MZ-2-002", "MZ-2-003", "MZ-2-004", "MZ-2-005",
                "MZ-2-006", "MZ-2-007", "MZ-2-008", "MZ-2-009", "MZ-2-010");
        verify(publisher, times(10)).publicar(eq("MZ-2"), any(), eq("valve"), eq("ON"), eq(Map.of("durationSec", 600)));
        ArgumentCaptor<SectorEntity> reg = ArgumentCaptor.forClass(SectorEntity.class);
        ArgumentCaptor<DetalleRiego> det = ArgumentCaptor.forClass(DetalleRiego.class);
        verify(historial, times(10)).registrarRiego(reg.capture(), det.capture(), eq("RiegoPorDeficitRule"), eq(T0.toEpochMilli()));
        assertThat(reg.getAllValues()).extracting(SectorEntity::getId).startsWith("MZ-2-001").hasSize(10);
        assertThat(det.getAllValues()).allSatisfy(d -> {
            assertThat(d.volumenL()).isEqualTo(5.0);
            assertThat(d.duracionSeg()).isEqualTo(600);
            assertThat(d.humedad()).isEqualTo(40.0);
        });
        assertThat(cola.pendientes("MZ-2")).hasSize(90);
    }

    @Test
    void unSegundoTickSinVencimientosNoAbreNada() {
        pedirZona("MZ-2", 100, 600);
        despacho.tick();

        reloj.avanzar(Duration.ofSeconds(10));
        despacho.tick();

        verify(publisher, times(10)).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void venceUnSectorMasCincoSegundosYAbreElSiguiente() {
        // MZ-2-003 dura 100 s; los demás 600 s.
        for (int n = 100; n >= 1; n--) {
            pedir("MZ-2", n, n == 3 ? 100 : 600);
        }
        despacho.tick();

        reloj.avanzar(Duration.ofMillis(104_999));        // 100 s + 5 s de margen todavía no pasaron
        despacho.tick();
        verify(publisher, times(10)).publicar(any(), any(), any(), any(), any());
        assertThat(despacho.estadoValvula("MZ-2-003")).isEqualTo("Regando");

        reloj.avanzar(Duration.ofMillis(1));              // ts + dur + 5 s == ahora: ya no está en curso
        despacho.tick();

        verify(publisher, times(11)).publicar(any(), any(), any(), any(), any());
        assertThat(sectoresPublicados()).last().isEqualTo("MZ-2-011");
        assertThat(despacho.estadoValvula("MZ-2-003")).isEqualTo("Cerrada");
        assertThat(despacho.estadoValvula("MZ-2-011")).isEqualTo("Regando");
        assertThat(despacho.estadoValvula("MZ-2-012")).isEqualTo("En cola");
    }

    @Test
    void dosZonasNoSeBloqueanEntreSi() {
        pedirZona("MZ-1", 15, 600);
        pedirZona("MZ-2", 15, 600);

        despacho.tick();

        verify(publisher, times(10)).publicar(eq("MZ-1"), any(), any(), any(), any());
        verify(publisher, times(10)).publicar(eq("MZ-2"), any(), any(), any(), any());
        // Con MZ-1 llena de sectores abiertos, MZ-2 no espera: sigue su propio cupo.
        reloj.avanzar(Duration.ofSeconds(10));
        despacho.tick();
        verify(publisher, times(20)).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void sectoresSimultaneosTresNuncaAbreMasDeTres() {
        usarSimultaneos(3);
        pedirZona("MZ-2", 100, 600);

        despacho.tick();
        reloj.avanzar(Duration.ofSeconds(300));
        despacho.tick();
        verify(publisher, times(3)).publicar(any(), any(), any(), any(), any());

        reloj.avanzar(Duration.ofSeconds(306));            // vencen los tres (600 + 5 s desde T0)
        despacho.tick();
        verify(publisher, times(6)).publicar(any(), any(), any(), any(), any());
        assertThat(sectoresPublicados()).containsExactly("MZ-2-001", "MZ-2-002", "MZ-2-003",
                "MZ-2-004", "MZ-2-005", "MZ-2-006");
    }

    @Test
    void sinNadaEnLaColaNoHaceNada() {
        despacho.tick();

        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        verify(historial, never()).registrarRiego(any(), any(), any(), anyLong());
    }

    // ------------------------------------------------------------------ 8.4

    private static ManualLockEntity bloqueoSector(String sectorId) {
        ManualLockEntity l = new ManualLockEntity();
        l.setSectorId(sectorId);
        return l;
    }

    private static ManualLockEntity bloqueoZona(String zonaId) {
        ManualLockEntity l = new ManualLockEntity();
        l.setZonaId(zonaId);
        return l;
    }

    @Test
    void bloqueoManualDelSectorDescartaLaSolicitudSinComando() {
        pedirZona("MZ-2", 12, 600);
        when(bloqueos.findByActiveTrue()).thenReturn(List.of(bloqueoSector("MZ-2-002")));

        despacho.tick();

        assertThat(sectoresPublicados()).doesNotContain("MZ-2-002").contains("MZ-2-001", "MZ-2-003");
        assertThat(sectoresPublicados()).hasSize(10);                 // los diez cupos los ocupan los demás
        assertThat(cola.contiene("MZ-2-002")).isFalse();
        assertThat(despacho.estadoValvula("MZ-2-002")).isEqualTo("Cerrada");
    }

    @Test
    void bloqueoManualDeLaZonaDescartaTodoLoPendienteDeLaZonaYNoToca_otras() {
        pedirZona("MZ-2", 100, 600);
        pedirZona("MZ-1", 3, 600);
        when(bloqueos.findByActiveTrue()).thenReturn(List.of(bloqueoZona("MZ-2")));

        despacho.tick();

        verify(publisher, never()).publicar(eq("MZ-2"), any(), any(), any(), any());
        verify(publisher, times(3)).publicar(eq("MZ-1"), any(), any(), any(), any());
        assertThat(cola.pendientes("MZ-2")).isEmpty();
    }

    @Test
    void elBloqueoDescartaAunqueNoHayaCupoLibre() {
        usarSimultaneos(1);
        pedirZona("MZ-2", 5, 600);
        despacho.tick();                                            // MZ-2-001 abierto, 4 en cola
        when(bloqueos.findByActiveTrue()).thenReturn(List.of(bloqueoSector("MZ-2-004")));

        reloj.avanzar(Duration.ofSeconds(1));
        despacho.tick();

        assertThat(cola.pendientes("MZ-2")).extracting(SolicitudRiego::sectorId)
                .containsExactly("MZ-2-002", "MZ-2-003", "MZ-2-005");
    }

    @Test
    void gatewayQueFallaNoRegistraElRiegoYLaSolicitudSigueEnCola() {
        pedirZona("MZ-2", 3, 600);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(false, "c", "broker caído"));

        despacho.tick();

        verify(historial, never()).registrarRiego(any(), any(), any(), anyLong());
        assertThat(cola.pendientes("MZ-2")).hasSize(3);
        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("En cola");

        // Vuelve el broker: el próximo tick lo despacha.
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c", null));
        reloj.avanzar(Duration.ofSeconds(10));
        despacho.tick();

        verify(historial, times(3)).registrarRiego(any(), any(), any(), anyLong());
        assertThat(cola.pendientes("MZ-2")).isEmpty();
    }

    @Test
    void unSectorQueYaNoExisteSeDescarta() {
        pedirZona("MZ-2", 2, 600);
        when(sectorRepository.findById("MZ-2-001")).thenReturn(Optional.empty());

        despacho.tick();

        assertThat(sectoresPublicados()).containsExactly("MZ-2-002");
        assertThat(cola.contiene("MZ-2-001")).isFalse();
    }

    @Test
    void unaSolicitudDeUnSectorQueYaEstaRegandoSeDescartaSinAbrirOtraVez() {
        pedir("MZ-2", 1, 600);
        despacho.tick();
        verify(publisher, times(1)).publicar(any(), any(), any(), any(), any());

        reloj.avanzar(Duration.ofSeconds(30));
        pedir("MZ-2", 1, 600);                                     // la telemetría lo vuelve a pedir
        despacho.tick();

        verify(publisher, times(1)).publicar(any(), any(), any(), any(), any());
        assertThat(cola.contiene("MZ-2-001")).isFalse();
    }

    // ------------------------------------------------------------------ transacción por riego

    @Test
    void elHistorialDeCadaRiegoSeConfirmaApenasDespuesDePublicarYNuncaConUnaTransaccionAbiertaMientrasSePublica() {
        pedirZona("MZ-2", 3, 600);

        despacho.tick();

        // Cada sector: publish → begin → commit. Si hubiera una transacción abierta al publicar el siguiente,
        // aparecería un "begin" antes del segundo "publish" sin su "commit".
        assertThat(eventos).containsExactly(
                "publish", "begin", "commit",
                "publish", "begin", "commit",
                "publish", "begin", "commit");
    }

    @Test
    void siElHistorialFallaTrasPublicarElSectorSigueRegandoNoSeRepublicaYElRestoDelTickSigue() {
        pedirZona("MZ-2", 4, 600);
        doThrow(new IllegalStateException("base caída")).when(historial)
                .registrarRiego(argThat(sec -> sec != null && sec.getId().equals("MZ-2-002")), any(), any(), anyLong());

        despacho.tick();

        // Los cuatro se publicaron: la falla del segundo no abortó el tick.
        assertThat(sectoresPublicados()).containsExactly("MZ-2-001", "MZ-2-002", "MZ-2-003", "MZ-2-004");
        // Sólo el registro del 002 se revirtió; los demás quedaron confirmados.
        assertThat(eventos.stream().filter("commit"::equals).count()).isEqualTo(3);
        assertThat(eventos.stream().filter("rollback"::equals).count()).isEqualTo(1);
        // El 002 sigue "Regando" (en memoria) y no sale de nuevo aunque la telemetría lo pida otra vez.
        assertThat(despacho.estadoValvula("MZ-2-002")).isEqualTo("Regando");
        reloj.avanzar(Duration.ofSeconds(30));
        pedir("MZ-2", 2, 600);
        despacho.tick();
        verify(publisher, times(4)).publicar(any(), any(), any(), any(), any());
        assertThat(cola.contiene("MZ-2-002")).isFalse();
    }

    @Test
    void siFallaElHistorialYSeReiniciaElDespachoElSectorNoSeVeEnCursoPeroNoHayDobleRiegoEnVivo() {
        // Documenta el límite: lo que no llegó al historial no se reconstruye tras un reinicio. Por eso el
        // registro se confirma de inmediato y por separado, para que esta ventana sea la mínima posible.
        pedir("MZ-2", 1, 600);
        doThrow(new IllegalStateException("base caída")).when(historial).registrarRiego(any(), any(), any(), anyLong());
        despacho.tick();

        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Regando");
        assertThat(nuevoDespacho().estadoValvula("MZ-2-001")).isEqualTo("Cerrada");
    }

    // ------------------------------------------------------------------ carrera cancelación / despacho

    @Test
    void unaCancelacionDeLaTelemetriaPosteriorALaCopiaDeLaColaFrenaElPublish() {
        pedirZona("MZ-2", 3, 600);
        // Entre la copia de pendientes() y el reclamo, el hilo MQTT retira el 002 (p. ej. R-04).
        when(sectorRepository.findById("MZ-2-002")).thenAnswer(i -> {
            cola.retirar("MZ-2", "MZ-2-002");
            return Optional.of(sector("MZ-2-002"));
        });

        despacho.tick();

        assertThat(sectoresPublicados()).containsExactly("MZ-2-001", "MZ-2-003");
        assertThat(cola.contiene("MZ-2-002")).isFalse();
        assertThat(despacho.estadoValvula("MZ-2-002")).isEqualTo("Cerrada");
    }

    @Test
    void unaSolicitudReemplazadaDuranteElTickNoSeBorraNiSePublicaLaVieja() {
        pedirZona("MZ-2", 3, 600);
        SolicitudRiego nueva = new SolicitudRiego("MZ-2", "MZ-2-002", 2,
                new DetalleRiego(7.0, 840, 38.0, false), "RiegoPorDeficitRule", reloj.instant());
        when(sectorRepository.findById("MZ-2-002")).thenAnswer(i -> {
            cola.solicitar(nueva);
            return Optional.of(sector("MZ-2-002"));
        });

        despacho.tick();

        assertThat(sectoresPublicados()).containsExactly("MZ-2-001", "MZ-2-003");
        // La solicitud nueva sigue en la cola, intacta, para el próximo tick.
        assertThat(cola.pendientes("MZ-2")).containsExactly(nueva);
    }

    @Test
    void unBloqueoManualPosteriorALaLecturaDeLosBloqueosDelTickSeRevalidaAntesDeAbrir() {
        pedirZona("MZ-2", 3, 600);
        // findByActiveTrue (al empezar el tick) dice que no hay bloqueos; justo antes de abrir el 002 sí.
        when(bloqueos.findBySectorIdAndActiveTrue("MZ-2-002")).thenReturn(List.of(bloqueoSector("MZ-2-002")));

        despacho.tick();

        assertThat(sectoresPublicados()).containsExactly("MZ-2-001", "MZ-2-003");
        assertThat(cola.contiene("MZ-2-002")).isFalse();
    }

    @Test
    void unBloqueoDeLaZonaPosteriorALaLecturaTambienSeRevalida() {
        pedirZona("MZ-2", 2, 600);
        when(bloqueos.findByZonaIdAndActiveTrue("MZ-2")).thenReturn(List.of(bloqueoZona("MZ-2")));

        despacho.tick();

        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        assertThat(cola.pendientes("MZ-2")).isEmpty();
    }

    // ------------------------------------------------------------------ topología regenerada

    @Test
    void reiniciarEstadoVaciaLaColaYLoQueEstabaRegando() {
        pedirZona("MZ-2", 12, 600);
        despacho.tick();
        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Regando");
        assertThat(despacho.estadoValvula("MZ-2-011")).isEqualTo("En cola");

        despacho.reiniciarEstado();

        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Cerrada");
        assertThat(despacho.estadoValvula("MZ-2-011")).isEqualTo("Cerrada");
        assertThat(cola.zonas()).isEmpty();
    }

    // ------------------------------------------------------------------ 8.5

    private HistorialEventoEntity riego(String sectorId, Instant ts, int duracionSeg) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("h-" + sectorId + ts);
        e.setSectorId(sectorId);
        e.setZonaId(zonaDe(sectorId));
        e.setTipo("Riego");
        e.setTs(ts.toEpochMilli());
        e.setDuracionSeg(duracionSeg);
        return e;
    }

    @Test
    void trasUnReinicioCuentaComoEnCursoLosRiegosConTsMasDuracionMasCincoSegundosMayorQueAhora() {
        List<HistorialEventoEntity> eventos = new ArrayList<>();
        for (int n = 1; n <= 10; n++) {
            eventos.add(riego(id("MZ-2", n), T0.minusSeconds(100), 600));   // terminan en T0 + 505 s
        }
        eventos.add(riego(id("MZ-2", 11), T0.minusSeconds(700), 600));      // venció hace 95 s
        eventos.add(riego(id("MZ-2", 12), T0.minusSeconds(605), 600));      // ts + dur + 5 s == ahora: venció
        when(historialRepository.riegosDesde(anyLong())).thenReturn(eventos);
        DespachoRiego reiniciado = nuevoDespacho();
        pedirZona("MZ-2", 30, 600);
        // Los diez en curso están en la cola otra vez (los volvió a pedir la telemetría): se descartan.
        reiniciado.tick();

        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        assertThat(reiniciado.estadoValvula(id("MZ-2", 1))).isEqualTo("Regando");
        assertThat(reiniciado.estadoValvula(id("MZ-2", 11))).isNotEqualTo("Regando");
        assertThat(reiniciado.estadoValvula(id("MZ-2", 12))).isNotEqualTo("Regando");

        reloj.avanzar(Duration.ofSeconds(506));                              // vencen los diez
        reiniciado.tick();
        verify(publisher, times(10)).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void laReconstruccionPideSoloLoReciente() {
        despacho.tick();   // cola vacía: no reconstruye
        pedir("MZ-2", 1, 600);
        despacho.tick();

        ArgumentCaptor<Long> desde = ArgumentCaptor.forClass(Long.class);
        verify(historialRepository).riegosDesde(desde.capture());
        // Un riego dura como mucho 1200 s + 5 s de margen: no hace falta mirar más atrás.
        assertThat(desde.getValue()).isEqualTo(T0.toEpochMilli() - (1200 + 5) * 1000L);
    }

    @Test
    void siLaBaseFallaAlReconstruirNoDespachaYReintenta() {
        when(historialRepository.riegosDesde(anyLong())).thenThrow(new IllegalStateException("base caída"))
                .thenReturn(List.of());
        pedir("MZ-2", 1, 600);

        despacho.tick();
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());

        despacho.tick();
        verify(publisher, times(1)).publicar(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ 8.6

    @Test
    void estadoValvula_regandoEnColaCerrada() {
        pedirZona("MZ-2", 12, 600);
        despacho.tick();

        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Regando");
        assertThat(despacho.estadoValvula("MZ-2-011")).isEqualTo("En cola");
        assertThat(despacho.estadoValvula("MZ-2-050")).isEqualTo("Cerrada");

        reloj.avanzar(Duration.ofSeconds(605));
        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Cerrada");
    }

    @Test
    void estadoValvula_noFallaSiLaBaseNoResponde() {
        when(historialRepository.riegosDesde(anyLong())).thenThrow(new IllegalStateException("base caída"));

        assertThat(despacho.estadoValvula("MZ-2-001")).isEqualTo("Cerrada");
    }

    // ------------------------------------------------------------------ catálogo y concurrencia

    @Test
    void declaraSuParametroParaQueElCatalogoLoMuestreEnUsadoPor() {
        assertThat(despacho.name()).isEqualTo("DespachoRiego");
        assertThat(despacho.parametros()).containsExactly(ParametrosRiego.SECTORES_SIMULTANEOS);
    }

    @Test
    void leerUnParametroNoDeclaradoEsUnError() {
        assertThatThrownBy(() -> despacho.numero(ParametrosRiego.CAUDAL_EMISOR))
                .isInstanceOf(ParametroNoDeclaradoException.class);
    }

    @Test
    void solicitudesConcurrentesMientrasSeDespachaNuncaAbrenMasDelCupoNiUnSectorDosVeces() throws Exception {
        for (int n = 1; n <= 50; n++) {
            pedir("MZ-2", n, 600);        // hay trabajo desde el primer tick: el test no depende de la largada
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicBoolean seguir = new AtomicBoolean(true);
        for (int h = 0; h < 4; h++) {
            pool.submit(() -> {
                while (seguir.get()) {
                    for (int n = 1; n <= 50; n++) {
                        pedir("MZ-2", n, 600);
                    }
                }
            });
        }
        for (int i = 0; i < 30; i++) {
            despacho.tick();
        }
        seguir.set(false);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        List<String> abiertos = sectoresPublicados();
        assertThat(abiertos).hasSize(10);
        Set<String> unicos = new HashSet<>(abiertos);
        assertThat(unicos).hasSameSizeAs(abiertos);
        assertThat(Collections.max(abiertos.stream().map(s -> Integer.parseInt(s.substring(s.lastIndexOf('-') + 1))).toList()))
                .isLessThanOrEqualTo(50);
    }

    @Test
    void cadaZonaDespachaEnSuPropioTopico() {
        pedir("MZ-3", 1, 120);

        despacho.tick();

        verify(publisher).publicar("MZ-3", "MZ-3-001", "valve", "ON", Map.of("durationSec", 120));
    }
}
