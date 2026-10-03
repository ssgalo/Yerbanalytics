package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RiegoCtx;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.rules.SustratoSaturadoRule;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tarea 10.4: el camino completo, sin Spring ni broker (las piezas reales cableadas a mano con repositorios y
 * publicador falsos, reloj fijo): telemetría → reglas → cola → despacho → comandos {@code valve ON}.
 *
 * <p>Va sin {@code @SpringBootTest} a propósito: levantar el contexto apunta a la base de desarrollo y al broker
 * reales, y el despacho publicaría comandos de verdad. Lo que no cubre (el cableado de Spring) lo cubren
 * {@code ReglasParametrosCatalogoRealTest} y {@code SchedulersConfigTest}, que sí arrancan el contexto.
 */
@DisplayName("Riego de punta a punta (telemetría → cola → despacho)")
class RiegoIntegracionTest {

    private static final int SECTORES = 100;

    private RelojDePrueba reloj;
    private NurseryService nursery;
    private DespachoRiego despacho;
    private ColaRiego cola;
    private ComandoActuadorPublisher publisher;
    private final List<HistorialEventoEntity> historia = Collections.synchronizedList(new ArrayList<>());
    private final List<String> comandos = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> duraciones = new HashMap<>();
    private ZonaEntity zona;
    private NurseryWatchdog watchdog;
    private HistorialService historialService;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(RiegoCtx.instante("10:05"));
        cola = new ColaRiego();

        // --- el vivero: MZ-2 con 100 sectores ---
        zona = RuleContextTestFactory.zonaBasica("MZ-2");
        zona.setName("Macro-zona 2");
        zona.setLastReadingTime(null);
        List<SectorEntity> sectores = new ArrayList<>();
        for (int n = 1; n <= SECTORES; n++) {
            SectorEntity s = RuleContextTestFactory.sectorBasico(id(n));
            s.setN(n);
            s.setZona(zona);
            sectores.add(s);
        }
        zona.setSectors(sectores);
        ZonaRepository zonaRepository = mock(ZonaRepository.class);
        when(zonaRepository.findById("MZ-2")).thenReturn(Optional.of(zona));
        when(zonaRepository.findAllWithSectors()).thenReturn(List.of(zona));
        SectorRepository sectorRepository = mock(SectorRepository.class);
        when(sectorRepository.findByZonaId("MZ-2")).thenReturn(sectores);
        when(sectorRepository.findById(anyString())).thenAnswer(i ->
                sectores.stream().filter(s -> s.getId().equals(i.getArgument(0))).findFirst());

        // --- el "historial": una lista en memoria detrás de los repositorios falsos ---
        historialService = mock(HistorialService.class);
        org.mockito.Mockito.doAnswer(i -> {
            SectorEntity s = i.getArgument(0);
            DetalleRiego d = i.getArgument(1);
            HistorialEventoEntity e = new HistorialEventoEntity();
            e.setId("h" + historia.size());
            e.setSectorId(s.getId());
            e.setZonaId(s.getZona().getId());
            e.setTipo("Riego");
            e.setTs(i.getArgument(3));
            e.setRegla(i.getArgument(2));
            e.setVolumenL(d.volumenL());
            e.setDuracionSeg(d.duracionSeg());
            historia.add(e);
            return null;
        }).when(historialService).registrarRiego(any(), any(), any(), anyLong());
        HistorialRepository historialRepository = mock(HistorialRepository.class);
        when(historialRepository.riegosDesde(anyLong())).thenAnswer(i ->
                historia.stream().filter(e -> e.getTs() >= (long) i.getArgument(0)).toList());
        when(historialRepository.ultimosPorSector(anyString(), anyLong())).thenAnswer(i -> {
            Map<String, HistorialRepository.UltimoEvento> out = new HashMap<>();
            for (HistorialEventoEntity e : new ArrayList<>(historia)) {
                if (e.getZonaId().equals(i.getArgument(0)) && e.getTs() >= (long) i.getArgument(1)) {
                    out.merge(e.getSectorId() + "|" + e.getTipo() + "|" + e.getRegla(), fila(e),
                            (a, b) -> a.getTs() >= b.getTs() ? a : b);
                }
            }
            return new ArrayList<>(out.values());
        });

        // --- el broker falso: anota cada comando valve ON ---
        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(anyString(), anyString(), eq("valve"), eq("ON"), any())).thenAnswer(i -> {
            comandos.add(i.getArgument(1));
            duraciones.put(i.getArgument(1), (Integer) ((Map<?, ?>) i.getArgument(4)).get("durationSec"));
            return new ComandoActuadorPublisher.Resultado(true, "c", null);
        });

        // --- catálogo, reglas, orquestador, executor y despacho reales ---
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        List<Rule> reglas = new ArrayList<>(List.of(new ManualLockRule(mock(ManualLockRepository.class)),
                new StaleSensorRule(), new CicloLecturaRiegoRule(), new SustratoSaturadoRule(),
                new DeficitCriticoRule(), new FueraDeVentanaRiegoRule(), new PausaTrasAplicacionRule(),
                new PosponerPorLluviaRule(), new RiegoPorDeficitRule()));
        TrazaEvaluacionStore trazas = new TrazaEvaluacionStore();
        RuleOrchestrator orquestador = new RuleOrchestrator(reglas, catalogo, trazas);
        ActionExecutor executor = new ActionExecutor(historialService, publisher, cola);
        ManualLockRepository bloqueos = mock(ManualLockRepository.class);
        despacho = new DespachoRiego(cola, publisher, historialService, historialRepository, sectorRepository,
                zonaRepository, bloqueos, catalogo, reloj, new TxSimple());

        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getEffectiveSpecs()).thenReturn(NurseryConstants.SPECS);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        nursery = new NurseryService(new NurseryProperties(), zonaRepository, sectorRepository, historialService,
                configuracion, mock(HardwareService.class), mock(TopologiaLayoutRepository.class), orquestador,
                executor, mock(WeatherService.class), mock(ManualLockRepository.class),
                mock(DiagnosticoService.class), trazas, catalogo, historialRepository, despacho, reloj, 20);
        watchdog = new NurseryWatchdog(zonaRepository, sectorRepository, orquestador, executor, configuracion,
                mock(WeatherService.class), mock(ManualLockRepository.class), trazas, reloj);
    }

    private static String id(int n) {
        return String.format("MZ-2-%03d", n);
    }

    private static HistorialRepository.UltimoEvento fila(HistorialEventoEntity e) {
        return new HistorialRepository.UltimoEvento() {
            @Override public String getSectorId() { return e.getSectorId(); }
            @Override public String getTipo() { return e.getTipo(); }
            @Override public String getRegla() { return e.getRegla(); }
            @Override public Long getTs() { return e.getTs(); }
        };
    }

    private void telemetria(double humedadSustrato) {
        nursery.updateTelemetry("MZ-2", new MqttTelemetryPayload("aa:bb", 90, -60, reloj.instant().getEpochSecond(),
                new MqttTelemetryPayload.MetricsPayload(humedadSustrato, 70.0, 22.0, 20.0, 40.0,
                        null, null, null, null, null)));
    }

    private List<String> sectoresEn(String estado) {
        return IntStream.rangeClosed(1, SECTORES).mapToObj(RiegoIntegracionTest::id)
                .filter(s -> despacho.estadoValvula(s).equals(estado)).collect(Collectors.toList());
    }

    private static List<String> rango(int desde, int hasta) {
        return IntStream.rangeClosed(desde, hasta).mapToObj(RiegoIntegracionTest::id).toList();
    }

    @Test
    @DisplayName("humSus 40 a las 10:05: 10 comandos valve ON de 600 s (5 L) y 90 sectores en cola")
    void primeraTanda() {
        telemetria(40.0);
        assertThat(cola.pendientes("MZ-2")).hasSize(SECTORES);
        assertThat(comandos).isEmpty();       // las reglas deciden; no abren nada

        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(duraciones.values()).containsOnly(600);
        assertThat(historia).hasSize(10).allSatisfy(e -> {
            assertThat(e.getVolumenL()).isEqualTo(5.0);
            assertThat(e.getRegla()).isEqualTo("RiegoPorDeficitRule");
        });
        assertThat(sectoresEn("Regando")).containsExactlyElementsOf(rango(1, 10));
        assertThat(sectoresEn("En cola")).containsExactlyElementsOf(rango(11, 100));
    }

    @Test
    @DisplayName("segunda telemetría a las 10:06: ningún comando nuevo para los 10 ya regados")
    void segundaTelemetriaNoRepite() {
        telemetria(40.0);
        despacho.tick();

        reloj.avanzar(Duration.ofMinutes(1));
        telemetria(40.0);
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        // Los diez regados ni vuelven a la cola ni se cuentan dos veces; los 90 siguen esperando.
        assertThat(sectoresEn("Regando")).containsExactlyElementsOf(rango(1, 10));
        assertThat(sectoresEn("En cola")).containsExactlyElementsOf(rango(11, 100));
        assertThat(historia).hasSize(10);
    }

    @Test
    @DisplayName("a los 605 s el tick abre los 10 siguientes y los primeros vuelven a 'Cerrada'")
    void siguienteTanda() {
        telemetria(40.0);
        despacho.tick();
        reloj.avanzar(Duration.ofMinutes(1));
        telemetria(40.0);
        despacho.tick();

        reloj.avanzar(Duration.ofSeconds(545));            // 605 s desde la primera tanda (10:05:00)
        telemetria(40.0);                                  // el nodo sigue publicando: sin lectura vigente no se abre nada
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 20));
        assertThat(sectoresEn("Regando")).containsExactlyElementsOf(rango(11, 20));
        assertThat(sectoresEn("Cerrada")).containsExactlyElementsOf(rango(1, 10));
        assertThat(sectoresEn("En cola")).containsExactlyElementsOf(rango(21, 100));
    }

    @Test
    @DisplayName("en el mismo ciclo los ya regados no vuelven a pedir riego, en el ciclo siguiente sí")
    void unRiegoPorCicloYVuelveEnElSiguiente() {
        telemetria(40.0);
        despacho.tick();
        reloj.avanzar(Duration.ofSeconds(606));
        telemetria(40.0);                                  // 10:15:06, todavía el ciclo 10:00-14:00
        despacho.tick();

        telemetria(40.0);

        assertThat(cola.contiene(id(1))).isFalse();        // regado en este ciclo
        assertThat(cola.contiene(id(15))).isFalse();       // regando ahora
        assertThat(cola.contiene(id(50))).isTrue();

        reloj.avanzar(Duration.ofMinutes(225));            // 14:00:06: ciclo nuevo
        telemetria(40.0);

        assertThat(cola.contiene(id(1))).isTrue();         // vuelve a ser elegible
    }

    @Test
    @DisplayName("humSus 34 (crítico): riega con el volumen máximo, 6 L en 720 s")
    void deficitCritico() {
        telemetria(34.0);

        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(duraciones.values()).containsOnly(720);
    }

    @Test
    @DisplayName("humSus 80 (saturado): no queda nada en la cola y no se publica nada")
    void saturado() {
        telemetria(80.0);

        despacho.tick();

        assertThat(cola.zonas()).isEmpty();
        assertThat(comandos).isEmpty();
    }

    @Test
    @DisplayName("una lectura saturada saca de la cola lo que estaba pendiente (R-04 cancela lo pendiente)")
    void saturadoCancelaLoPendiente() {
        telemetria(40.0);
        despacho.tick();
        assertThat(cola.pendientes("MZ-2")).hasSize(90);

        reloj.avanzar(Duration.ofMinutes(1));
        telemetria(76.0);

        assertThat(cola.pendientes("MZ-2")).isEmpty();
        assertThat(sectoresEn("Regando")).containsExactlyElementsOf(rango(1, 10));   // los abiertos siguen
    }

    @Test
    @DisplayName("fuera de la ventana (19:00) con humSus 40 no se riega; con 30 sí (R-02)")
    void fueraDeVentana() {
        reloj.avanzar(Duration.ofHours(9));                // 19:05
        telemetria(40.0);
        assertThat(cola.zonas()).isEmpty();

        reloj.avanzar(Duration.ofSeconds(30));
        telemetria(30.0);
        assertThat(cola.pendientes("MZ-2")).hasSize(SECTORES);
    }

    @Test
    @DisplayName("el barrido del watchdog no encola, no retira ni duplica riegos: la cola queda como estaba")
    void elBarridoNoTocaLaCola() {
        telemetria(40.0);
        despacho.tick();
        assertThat(cola.pendientes("MZ-2")).hasSize(90);

        for (int pasada = 1; pasada <= 3; pasada++) {
            reloj.avanzar(Duration.ofMinutes(5));         // cada pasada del watchdog, sin lectura fresca
            watchdog.evaluarTodos();
        }

        // Evalúa sin métricas: ni R-01 ni R-02 pueden pedir riego, y el barrido no retira lo pendiente.
        assertThat(cola.pendientes("MZ-2")).extracting(p -> p.sectorId()).containsExactlyElementsOf(rango(11, 100));
        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(historia).hasSize(10);
    }

    @Test
    @DisplayName("con la lectura vieja el barrido corta (StaleSensorRule) y el despacho NO abre nada más ni deja la cola")
    void barridoConLecturaViejaNoRiega() {
        telemetria(40.0);
        despacho.tick();                                   // abre 1-10; los 90 restantes esperan
        reloj.avanzar(Duration.ofSeconds(700));            // el nodo murió: la lectura es mucho más vieja que 90 s

        watchdog.evaluarTodos();
        despacho.tick();                                   // cupo libre, pero con datos viejos no se abre ninguna

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(cola.zonas()).isEmpty();
    }

    // ------------------------------------------------------------------ el registro de inacción sólo cuando cambia

    private int filasDeInaccion() {
        return org.mockito.Mockito.mockingDetails(historialService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("registrarInaccion")).mapToInt(i -> 1).sum();
    }

    @Test
    @DisplayName("60 mensajes idénticos del nodo (30 min) escriben una sola tanda de filas Info; al cambiar la decisión se registra otra")
    void inaccionSoloCuandoCambia() {
        telemetria(40.0);
        int primeraTanda = filasDeInaccion();
        assertThat(primeraTanda).as("la primera evaluación registra el conjunto de cada sector").isGreaterThan(SECTORES);

        for (int mensaje = 0; mensaje < 59; mensaje++) {
            reloj.avanzar(Duration.ofSeconds(30));
            telemetria(40.0);
        }
        assertThat(filasDeInaccion()).as("en régimen estable: cero filas por mensaje").isEqualTo(primeraTanda);

        telemetria(80.0);                                  // saturado: R-04 corta, otras reglas pasan a "no alcanzada"
        assertThat(filasDeInaccion()).isGreaterThan(primeraTanda);
    }

    @Test
    @DisplayName("el barrido del watchdog no escribe filas que la telemetría ya registró ni repite las suyas")
    void barridoNoDuplicaFilas() {
        telemetria(40.0);
        reloj.avanzar(Duration.ofSeconds(30));
        telemetria(40.0);
        int base = filasDeInaccion();

        for (int pasada = 0; pasada < 3; pasada++) {
            watchdog.evaluarTodos();
        }

        // La primera pasada registra la decisión propia del barrido (otra: sin métricas), las demás nada.
        int tras1 = filasDeInaccion();
        reloj.avanzar(Duration.ofSeconds(30));
        telemetria(40.0);
        watchdog.evaluarTodos();
        assertThat(filasDeInaccion()).isEqualTo(tras1);
        assertThat(tras1).isGreaterThanOrEqualTo(base);
    }

    // ------------------------------------------------------------------ la ronda se completa (cancelación explícita)

    /** Avanza {@code segundos} publicando una lectura cada 30 s y despachando, como el nodo real y el scheduler. */
    private void avanzarConTelemetria(int segundos, double humedad) {
        for (int t = 0; t < segundos; t += 30) {
            reloj.avanzar(Duration.ofSeconds(30));
            telemetria(humedad);
            despacho.tick();
        }
    }

    /** Mantiene fresca la lectura de la zona SIN pasar por las reglas: aísla la revalidación del despacho. */
    private void lecturaFrescaSinReglas() {
        zona.setLastReadingTime(reloj.millis());
        zona.setHumSusTs(reloj.millis());
    }

    @Test
    @DisplayName("humSus 30 → 100 en cola; la primera tanda moja el testigo y la lectura sube a 65: los 90 restantes SIGUEN y se riegan")
    void laRondaSeCompletaAunqueLaHumedadSeRecupere() {
        telemetria(30.0);
        despacho.tick();
        assertThat(cola.pendientes("MZ-2")).hasSize(90);

        avanzarConTelemetria(30, 65.0);                    // la lectura ya es 65: R-01 y R-02 dejan de pedir riego

        assertThat(cola.pendientes("MZ-2")).as("recuperarse la humedad no cancela la ronda").hasSize(90);

        avanzarConTelemetria(9 * 750 + 60, 65.0);         // las nueve tandas que faltan (tick cada 30 s)

        assertThat(comandos).containsExactlyElementsOf(rango(1, 100));
        assertThat(cola.zonas()).isEmpty();
        assertThat(historia).hasSize(100);
    }

    @Test
    @DisplayName("lo mismo con R-01 (humSus 40 → 65): los 90 restantes siguen y se riegan en las tandas siguientes")
    void laRondaDeR01TambienSeCompleta() {
        telemetria(40.0);
        despacho.tick();

        avanzarConTelemetria(30, 65.0);
        assertThat(cola.pendientes("MZ-2")).hasSize(90);

        avanzarConTelemetria(9 * 630 + 60, 65.0);

        assertThat(comandos).containsExactlyElementsOf(rango(1, 100));
    }

    @Test
    @DisplayName("la lectura sube a 76 (R-04): se cancelan los 90 pendientes de una ronda de R-02; los abiertos siguen")
    void saturadoCancelaLaRondaDeR02() {
        telemetria(30.0);
        despacho.tick();

        avanzarConTelemetria(30, 76.0);

        assertThat(cola.pendientes("MZ-2")).isEmpty();
        assertThat(sectoresEn("Regando")).containsExactlyElementsOf(rango(1, 10));
    }

    // ------------------------------------------------------------------ el despacho revalida con datos actuales

    @Test
    @DisplayName("el nodo muere con 90 en cola: el despacho no abre más válvulas y los pendientes se descartan (S-02)")
    void nodoMuertoNoSigueRegando() {
        telemetria(30.0);
        despacho.tick();
        reloj.avanzar(Duration.ofSeconds(60));
        telemetria(30.0);                                  // última lectura; después, silencio

        reloj.avanzar(Duration.ofSeconds(700));
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(cola.zonas()).isEmpty();
    }

    @Test
    @DisplayName("humedad de sustrato congelada (la sonda cayó, el resto de la lectura es fresco): no se abre nada")
    void humedadCongeladaNoRiega() {
        telemetria(30.0);
        despacho.tick();
        reloj.avanzar(Duration.ofSeconds(700));
        zona.setLastReadingTime(reloj.millis());           // el nodo sigue publicando temperatura y luz...
                                                           // ...pero humSusTs quedó donde estaba
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(cola.zonas()).isEmpty();
    }

    @Test
    @DisplayName("una solicitud de R-01 encolada a las 17:58 no se despacha de noche aunque no llegue telemetría de reglas")
    void r01NoSeDespachaFueraDeVentana() {
        reloj.avanzar(Duration.ofMinutes(473));            // 17:58
        telemetria(40.0);
        despacho.tick();                                   // abre 1-10 dentro de la ventana

        reloj.avanzar(Duration.ofSeconds(725));            // 18:10: la ventana cerró
        lecturaFrescaSinReglas();
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(cola.zonas()).isEmpty();
    }

    @Test
    @DisplayName("la ventana horaria NO frena a R-02: de noche se completa la ronda del déficit crítico")
    void r02NoDependeDeLaVentana() {
        reloj.avanzar(Duration.ofHours(13).plusMinutes(25));   // 23:30
        telemetria(30.0);
        despacho.tick();

        reloj.avanzar(Duration.ofSeconds(725));
        lecturaFrescaSinReglas();
        zona.setHumSusRaw(30.0);
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 20));
    }

    @Test
    @DisplayName("la humedad actual de la zona pasó el bloqueo de saturación: no se abre más, también para R-02")
    void saturacionActualDescartaAlDespachar() {
        telemetria(30.0);
        despacho.tick();
        reloj.avanzar(Duration.ofSeconds(725));
        lecturaFrescaSinReglas();
        zona.setHumSusRaw(76.0);
        despacho.tick();

        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(cola.zonas()).isEmpty();
    }

    // ------------------------------------------------------------------ el riego sobrevive en memoria si falla el historial

    @Test
    @DisplayName("si falla el registro en historial, el ciclo igual recuerda el riego y R-01 no vuelve a regar en el ciclo")
    void riegoRegistradoSoloEnMemoria() {
        org.mockito.Mockito.doThrow(new IllegalStateException("base caída")).when(historialService)
                .registrarRiego(any(), any(), any(), anyLong());
        telemetria(40.0);
        despacho.tick();
        assertThat(comandos).containsExactlyElementsOf(rango(1, 10));
        assertThat(historia).isEmpty();

        reloj.avanzar(Duration.ofSeconds(606));            // ya cerró (duración + 5 s), mismo ciclo 10:00-14:00
        telemetria(40.0);

        assertThat(cola.contiene(id(1))).as("ya regó en este ciclo").isFalse();
        assertThat(cola.contiene(id(50))).isTrue();
    }

    /** Administrador de transacciones sin efecto: el despacho sólo necesita que exista. */
    private static final class TxSimple implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
