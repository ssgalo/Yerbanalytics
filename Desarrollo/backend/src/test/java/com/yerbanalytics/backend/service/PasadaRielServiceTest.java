package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.PasadaProperties;
import com.yerbanalytics.backend.dto.EstadoOrden;
import com.yerbanalytics.backend.dto.Pasada;
import com.yerbanalytics.backend.dto.PasoPasada;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoRielPublisher;
import com.yerbanalytics.backend.mqtt.ComandoRielPublisher.Resultado;
import com.yerbanalytics.backend.mqtt.EventoRiel;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PasadaRielService")
class PasadaRielServiceTest {

    private final CapturaService capturas = mock(CapturaService.class);
    private final ComandoRielPublisher riel = mock(ComandoRielPublisher.class);
    private final ZonaRepository zonaRepo = mock(ZonaRepository.class);
    private final DiagnosticoRepository diagRepo = mock(DiagnosticoRepository.class);
    private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2025-10-03T12:00:00Z"));
    private final AtomicInteger seq = new AtomicInteger();
    /** Lo que dice la otra parte del guardia (las secuencias): vacío = libre. */
    private final AtomicReference<Optional<String>> secuenciaOcupada = new AtomicReference<>(Optional.empty());
    private final GuardiaHardware guardia = GuardiaHardwareTest.guardiaCon(() -> secuenciaOcupada.get());
    private PasadaRielService service;

    @BeforeEach
    void preparar() {
        // La topología: MZ-10 y MZ-2 desordenadas (el orden lexicográfico elegiría MZ-10) y
        // sectores fuera de orden dentro de la zona.
        when(zonaRepo.findAllWithSectors()).thenReturn(List.of(
                zona("MZ-10", "MZ-10-001", "MZ-10-002"),
                zona("MZ-2", "MZ-2-002", "MZ-2-001", "MZ-2-003")));
        when(capturas.hayCanalAbierto()).thenReturn(true);
        when(riel.irA(anyInt())).thenAnswer(i -> new Resultado(true, "cmd-" + seq.incrementAndGet(), null));
        when(riel.home()).thenAnswer(i -> new Resultado(true, "cmd-" + seq.incrementAndGet(), null));
        when(riel.irA(anyInt(), anyString())).thenAnswer(i -> new Resultado(true, i.getArgument(1), null));
        when(riel.home(anyString())).thenAnswer(i -> new Resultado(true, i.getArgument(0), null));
        service = new PasadaRielService(capturas, riel, zonaRepo, diagRepo, new PasadaProperties(), reloj, guardia);
    }

    // ---------------------------------------------------------------- helpers

    private static ZonaEntity zona(String id, String... sectores) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        List<SectorEntity> ss = new ArrayList<>();
        for (String s : sectores) {
            SectorEntity se = new SectorEntity();
            se.setId(s);
            se.setZona(z);
            ss.add(se);
        }
        z.setSectors(ss);
        return z;
    }

    private static EstadoOrden orden(String ordenId, String estado, String capturaId) {
        return new EstadoOrden(ordenId, "S", "Z", 1, estado, 1, null, null, capturaId,
                capturaId != null ? "/api/capturas/" + capturaId + "/imagen" : null, 0L, null, 0L);
    }

    private static EstadoOrden ordenError(String motivo, String detalle) {
        return new EstadoOrden("o", "S", "Z", 1, "ERROR", 3, motivo, detalle, null, null, 0L, null, 0L);
    }

    private PasoPasada paso(int n) {
        return service.estado().orElseThrow().pasos().get(n - 1);
    }

    private Pasada pasada() {
        return service.estado().orElseThrow();
    }

    private void evento(String cmd, String status) {
        service.registrarEvento(new EventoRiel(cmd, status, null, 0L, null, null));
        service.tick();
    }

    private void evento(String cmd, String status, String codigo, String detalle) {
        service.registrarEvento(new EventoRiel(cmd, status, null, 0L, codigo, detalle));
        service.tick();
    }

    /** Resuelve el paso MOVER/HOME en curso con LLEGO. */
    private void llego(int n) {
        evento(paso(n).commandId(), "LLEGO");
    }

    /** Resuelve la captura del paso n con una orden RECIBIDA. */
    private void foto(int n, String capturaId) {
        String ordenId = paso(n).ordenId();
        when(capturas.consultarOrden(ordenId)).thenReturn(orden(ordenId, "RECIBIDA", capturaId));
        service.tick();
    }

    private void iniciarYLlegarAPos1() {
        when(capturas.emitirOrden(anyString(), anyInt())).thenAnswer(i ->
                orden("orden-" + i.getArgument(0), "ENTREGADA", null));
        service.iniciar();
        llego(1);
    }

    // ---------------------------------------------------------------- iniciar

    @Test
    @DisplayName("iniciar arma los 5 pasos con los dos primeros sectores de la zona de menor número y publica IR_A 1")
    void iniciarArmaLosCincoPasos() {
        Pasada p = service.iniciar();

        assertThat(p.estado()).isEqualTo("EN_CURSO");
        assertThat(p.iniciadaEn()).isEqualTo(reloj.millis());
        assertThat(p.finalizadaEn()).isNull();
        assertThat(p.cancelacionSolicitada()).isFalse();
        assertThat(p.error()).isNull();
        assertThat(p.pasos()).extracting(PasoPasada::tipo)
                .containsExactly("MOVER", "CAPTURAR", "MOVER", "CAPTURAR", "HOME");
        assertThat(p.pasos()).extracting(PasoPasada::posicion).containsExactly(1, 1, 2, 2, 0);
        assertThat(p.pasos()).extracting(PasoPasada::sectorId)
                .containsExactly(null, "MZ-2-001", null, "MZ-2-002", null);
        assertThat(p.pasos()).extracting(PasoPasada::estado)
                .containsExactly("EN_CURSO", "PENDIENTE", "PENDIENTE", "PENDIENTE", "PENDIENTE");
        assertThat(p.pasos().get(0).commandId()).isEqualTo("cmd-1");
        assertThat(p.pasos().get(0).iniciadoEn()).isEqualTo(reloj.millis());
        verify(riel).irA(1);
    }

    @Test
    @DisplayName("409 si ya hay una pasada en curso, y no vuelve a publicar")
    void rechazaSegundaPasada() {
        service.iniciar();

        assertThatThrownBy(() -> service.iniciar())
                .isInstanceOf(PasadaRechazadaException.class)
                .hasMessage("Ya hay una pasada en curso.");
        verify(riel, times(1)).irA(anyInt());
    }

    @Test
    @DisplayName("409 con el motivo del guardia si hay una secuencia en curso, y no publica nada")
    void rechazaConUnaSecuenciaEnCurso() {
        secuenciaOcupada.set(Optional.of("Hay una secuencia de riego en curso."));

        assertThatThrownBy(() -> service.iniciar())
                .isInstanceOf(PasadaRechazadaException.class)
                .hasMessage("Hay una secuencia de riego en curso.");
        verifyNoInteractions(riel);
        assertThat(service.estado()).isEmpty();
    }

    @Test
    @DisplayName("ocupadoPor() sólo informa ocupado con la pasada EN_CURSO")
    void ocupadoPorSoloConLaPasadaEnCurso() {
        assertThat(service.ocupadoPor()).as("nunca hubo una pasada").isEmpty();

        service.iniciar();
        assertThat(service.ocupadoPor()).contains("Hay una pasada del riel en curso.");

        // Terminada (cancelada y con el HOME resuelto), vuelve a quedar libre.
        service.cancelar();
        llego(5);
        assertThat(pasada().estado()).isEqualTo("CANCELADA");
        assertThat(service.ocupadoPor()).isEmpty();
    }

    @Test
    @DisplayName("409 si la zona tiene menos de 2 sectores")
    void rechazaZonaConPocosSectores() {
        when(zonaRepo.findAllWithSectors()).thenReturn(List.of(zona("MZ-1", "MZ-1-001")));

        assertThatThrownBy(() -> service.iniciar())
                .isInstanceOf(PasadaRechazadaException.class)
                .hasMessage("La pasada necesita al menos 2 sectores en MZ-1 (hay 1).");
        verifyNoInteractions(riel);
    }

    @Test
    @DisplayName("409 si no hay topología")
    void rechazaSinTopologia() {
        when(zonaRepo.findAllWithSectors()).thenReturn(List.of());

        assertThatThrownBy(() -> service.iniciar()).isInstanceOf(PasadaRechazadaException.class);
        verifyNoInteractions(riel);
    }

    @Test
    @DisplayName("409 sin dispositivo de captura conectado, y no publica nada")
    void rechazaSinCanalAbierto() {
        when(capturas.hayCanalAbierto()).thenReturn(false);

        assertThatThrownBy(() -> service.iniciar())
                .isInstanceOf(PasadaRechazadaException.class)
                .hasMessage("No hay ningún dispositivo de captura conectado.");
        verifyNoInteractions(riel);
    }

    @Test
    @DisplayName("terminada la pasada, se puede iniciar otra")
    void sePuedeIniciarOtraTrasTerminar() {
        String anterior = service.iniciar().id();
        service.cancelar();
        llego(5);
        assertThat(pasada().estado()).isEqualTo("CANCELADA");

        Pasada nueva = service.iniciar();

        assertThat(nueva.estado()).isEqualTo("EN_CURSO");
        assertThat(nueva.id()).isNotEqualTo(anterior);
    }

    @Test
    @DisplayName("publicación fallida: el paso queda en ERROR PUBLICACION_FALLIDA y se va a home")
    void publicacionFallida() {
        when(riel.irA(1)).thenReturn(new Resultado(false, "x", "broker caído"));

        Pasada p = service.iniciar();

        assertThat(p.pasos().get(0).estado()).isEqualTo("ERROR");
        assertThat(p.pasos().get(0).codigoError()).isEqualTo("PUBLICACION_FALLIDA");
        assertThat(p.pasos().get(0).detalle()).isEqualTo("No se pudo publicar el comando al broker: broker caído");
        assertThat(p.pasos().get(4).estado()).isEqualTo("EN_CURSO");
        assertThat(p.error()).isEqualTo(p.pasos().get(0).detalle());
    }

    // ---------------------------------------------------------------- camino feliz

    @Test
    @DisplayName("camino feliz: los 5 pasos en orden hasta COMPLETADA")
    void caminoFeliz() {
        when(capturas.emitirOrden(anyString(), anyInt())).thenAnswer(i ->
                orden("orden-" + i.getArgument(0), "ENTREGADA", null));
        service.iniciar();

        evento(paso(1).commandId(), "ACEPTADO");
        assertThat(paso(1).estado()).isEqualTo("EN_CURSO");

        llego(1);
        assertThat(paso(1).estado()).isEqualTo("OK");
        assertThat(paso(1).terminadoEn()).isNotNull();
        assertThat(paso(2).estado()).isEqualTo("EN_CURSO");
        assertThat(paso(2).ordenId()).isEqualTo("orden-MZ-2-001");
        assertThat(paso(2).estadoOrden()).isEqualTo("ENTREGADA");
        verify(capturas).emitirOrden("MZ-2-001", 1);

        foto(2, "CAP-000001");
        assertThat(paso(2).estado()).isEqualTo("OK");
        assertThat(paso(2).capturaId()).isEqualTo("CAP-000001");
        assertThat(paso(2).imagenUrl()).isEqualTo("/api/capturas/CAP-000001/imagen");
        assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
        verify(riel).irA(2);

        llego(3);
        verify(capturas).emitirOrden("MZ-2-002", 2);
        foto(4, "CAP-000002");
        assertThat(paso(5).estado()).isEqualTo("EN_CURSO");
        verify(riel).home();

        reloj.avanzar(Duration.ofSeconds(30));
        llego(5);

        Pasada p = pasada();
        assertThat(p.estado()).isEqualTo("COMPLETADA");
        assertThat(p.finalizadaEn()).isEqualTo(reloj.millis());
        assertThat(p.error()).isNull();
        assertThat(p.pasos()).extracting(PasoPasada::estado).containsOnly("OK");
    }

    @Test
    @DisplayName("un evento con commandId ajeno se ignora")
    void eventoAjenoSeIgnora() {
        service.iniciar();

        evento("de-otra-pasada", "LLEGO");
        evento("de-otra-pasada", "ERROR", "FIN_DE_CARRERA", "x");

        assertThat(paso(1).estado()).isEqualTo("EN_CURSO");
        assertThat(pasada().estado()).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("sin pasada, un evento no hace nada")
    void eventoSinPasada() {
        evento("x", "LLEGO");

        assertThat(service.estado()).isEmpty();
    }

    // ---------------------------------------------------------------- riel que falla

    @Test
    @DisplayName("sin evento a los 5 s republica el mismo comando; a los 10 s RIEL_SIN_RESPUESTA y se va a home")
    void rielSinRespuesta() {
        service.iniciar();
        String cmd = paso(1).commandId();

        reloj.avanzar(Duration.ofSeconds(4));
        service.tick();
        verify(riel, never()).irA(anyInt(), anyString());

        reloj.avanzar(Duration.ofSeconds(1));
        service.tick();
        verify(riel).irA(1, cmd);
        assertThat(paso(1).estado()).isEqualTo("EN_CURSO");

        reloj.avanzar(Duration.ofSeconds(1));
        service.tick();
        verify(riel, times(1)).irA(anyInt(), anyString());   // una sola republicación

        reloj.avanzar(Duration.ofSeconds(4));
        service.tick();

        assertThat(paso(1).estado()).isEqualTo("ERROR");
        assertThat(paso(1).codigoError()).isEqualTo("RIEL_SIN_RESPUESTA");
        assertThat(paso(1).detalle())
                .isEqualTo("El riel no respondió. ¿El ESP32 está encendido y conectado al broker?");
        assertThat(paso(2).estado()).isEqualTo("OMITIDO");
        assertThat(paso(3).estado()).isEqualTo("OMITIDO");
        assertThat(paso(4).estado()).isEqualTo("OMITIDO");
        assertThat(paso(5).estado()).isEqualTo("EN_CURSO");
        verify(riel).home();

        llego(5);
        assertThat(pasada().estado()).isEqualTo("FALLIDA");
        assertThat(pasada().error()).startsWith("El riel no respondió");
    }

    @Test
    @DisplayName("si el riel ya aceptó, no se republica ni se da por caído")
    void aceptadoNoRepublica() {
        service.iniciar();
        evento(paso(1).commandId(), "ACEPTADO");

        reloj.avanzar(Duration.ofSeconds(30));
        service.tick();

        verify(riel, never()).irA(anyInt(), anyString());
        assertThat(paso(1).estado()).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("ERROR FIN_DE_CARRERA en un MOVER: omite lo pendiente, va a home y termina FALLIDA")
    void errorDelRielEnMover() {
        service.iniciar();

        evento(paso(1).commandId(), "ERROR", "FIN_DE_CARRERA", "se activó el final");

        assertThat(paso(1).estado()).isEqualTo("ERROR");
        assertThat(paso(1).codigoError()).isEqualTo("FIN_DE_CARRERA");
        assertThat(paso(1).detalle())
                .isEqualTo("El riel tocó un final de carrera antes de llegar a la posición 1.");
        assertThat(paso(2).estado()).isEqualTo("OMITIDO");
        assertThat(paso(3).estado()).isEqualTo("OMITIDO");
        assertThat(paso(4).estado()).isEqualTo("OMITIDO");
        assertThat(paso(5).estado()).isEqualTo("EN_CURSO");

        llego(5);
        assertThat(pasada().estado()).isEqualTo("FALLIDA");
    }

    @Test
    @DisplayName("ERROR en HOME: FALLIDA sin reintentar")
    void errorEnHomeNoReintenta() {
        service.iniciar();
        evento(paso(1).commandId(), "ERROR", "FIN_DE_CARRERA", "x");

        evento(paso(5).commandId(), "ERROR", "HOME_NO_ENCONTRADO", "x");

        assertThat(paso(5).estado()).isEqualTo("ERROR");
        assertThat(paso(5).detalle()).isEqualTo("El riel no encontró el final de carrera de home.");
        assertThat(pasada().estado()).isEqualTo("FALLIDA");
        verify(riel, times(1)).home();
        // el error de la pasada es el del PRIMER paso fallido
        assertThat(pasada().error()).startsWith("El riel tocó un final de carrera");
    }

    @Test
    @DisplayName("códigos del ESP32: texto legible para cada uno y para los desconocidos")
    void detallesLegibles() {
        service.iniciar();
        evento(paso(1).commandId(), "ERROR", "COMANDO_INVALIDO", "posicion fuera de rango");
        assertThat(paso(1).detalle()).isEqualTo("El ESP32 rechazó el comando: posicion fuera de rango");

        PasadaRielService otro = new PasadaRielService(capturas, riel, zonaRepo, diagRepo, new PasadaProperties(), reloj, guardia);
        otro.iniciar();
        String c = otro.estado().orElseThrow().pasos().get(0).commandId();
        otro.registrarEvento(new EventoRiel(c, "ERROR", null, 0L, "REEMPLAZADO", null));
        otro.tick();
        assertThat(otro.estado().orElseThrow().pasos().get(0).detalle())
                .isEqualTo("El movimiento fue interrumpido por otro comando.");

        PasadaRielService raro = new PasadaRielService(capturas, riel, zonaRepo, diagRepo, new PasadaProperties(), reloj, guardia);
        raro.iniciar();
        String c2 = raro.estado().orElseThrow().pasos().get(0).commandId();
        raro.registrarEvento(new EventoRiel(c2, "ERROR", null, 0L, "RARO", "algo"));
        raro.tick();
        assertThat(raro.estado().orElseThrow().pasos().get(0).codigoError()).isEqualTo("RARO");
        assertThat(raro.estado().orElseThrow().pasos().get(0).detalle())
                .isEqualTo("El riel informó un error: RARO algo");
    }

    @Test
    @DisplayName("TIMEOUT_MOVIMIENTO a los 120 s")
    void timeoutMovimiento() {
        service.iniciar();
        evento(paso(1).commandId(), "ACEPTADO");

        reloj.avanzar(Duration.ofSeconds(120));
        service.tick();
        assertThat(paso(1).estado()).isEqualTo("EN_CURSO");

        reloj.avanzar(Duration.ofSeconds(1));
        service.tick();

        assertThat(paso(1).estado()).isEqualTo("ERROR");
        assertThat(paso(1).codigoError()).isEqualTo("TIMEOUT_MOVIMIENTO");
        assertThat(paso(1).detalle()).isEqualTo("El riel no llegó a la posición 1 en 120 s.");
    }

    // ---------------------------------------------------------------- capturas

    @Test
    @DisplayName("TIMEOUT_CAPTURA a los 240 s: el paso falla pero la pasada sigue")
    void timeoutCaptura() {
        iniciarYLlegarAPos1();
        String ordenId = paso(2).ordenId();
        when(capturas.consultarOrden(ordenId)).thenReturn(orden(ordenId, "ENTREGADA", null));

        reloj.avanzar(Duration.ofSeconds(240));
        service.tick();
        assertThat(paso(2).estado()).isEqualTo("EN_CURSO");

        reloj.avanzar(Duration.ofSeconds(1));
        service.tick();

        assertThat(paso(2).estado()).isEqualTo("ERROR");
        assertThat(paso(2).codigoError()).isEqualTo("TIMEOUT_CAPTURA");
        assertThat(paso(2).detalle()).isEqualTo("La foto del sector MZ-2-001 no llegó en 240 s.");
        assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("orden en ERROR: ORDEN_FALLIDA, sigue a la posición 2 y termina FALLIDA")
    void ordenFallida() {
        iniciarYLlegarAPos1();
        String ordenId = paso(2).ordenId();
        when(capturas.consultarOrden(ordenId)).thenReturn(ordenError("SIN_PERMISO", "camara denegada"));

        service.tick();

        assertThat(paso(2).estado()).isEqualTo("ERROR");
        assertThat(paso(2).codigoError()).isEqualTo("ORDEN_FALLIDA");
        assertThat(paso(2).detalle()).isEqualTo("El celular no pudo sacar la foto: SIN_PERMISO camara denegada");
        assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
        verify(riel).irA(2);

        llego(3);
        foto(4, "CAP-000002");
        llego(5);

        assertThat(pasada().estado()).isEqualTo("FALLIDA");
        assertThat(pasada().error()).startsWith("El celular no pudo sacar la foto");
        assertThat(paso(4).estado()).isEqualTo("OK");
    }

    @Test
    @DisplayName("emitirOrden que lanza: ORDEN_FALLIDA con el mensaje")
    void emitirOrdenLanza() {
        when(capturas.emitirOrden(anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("El sector 'MZ-2-001' no existe en la topología vigente."));
        service.iniciar();

        llego(1);

        assertThat(paso(2).estado()).isEqualTo("ERROR");
        assertThat(paso(2).codigoError()).isEqualTo("ORDEN_FALLIDA");
        assertThat(paso(2).detalle()).contains("no existe en la topología vigente");
        assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
    }

    // ---------------------------------------------------------------- cancelar

    @Test
    @DisplayName("cancelar en una captura: paso y pendientes OMITIDO, HOME, y al terminar CANCELADA")
    void cancelarEnCaptura() {
        iniciarYLlegarAPos1();

        Pasada p = service.cancelar();

        assertThat(p.cancelacionSolicitada()).isTrue();
        assertThat(p.estado()).isEqualTo("EN_CURSO");
        assertThat(paso(2).estado()).isEqualTo("OMITIDO");
        assertThat(paso(2).detalle()).isEqualTo("Cancelada por el operador");
        assertThat(paso(3).estado()).isEqualTo("OMITIDO");
        assertThat(paso(4).estado()).isEqualTo("OMITIDO");
        assertThat(paso(5).estado()).isEqualTo("EN_CURSO");
        verify(riel).home();

        llego(5);

        assertThat(pasada().estado()).isEqualTo("CANCELADA");
        assertThat(pasada().finalizadaEn()).isNotNull();
    }

    @Test
    @DisplayName("cancelar a mitad de un movimiento: el evento viejo ya no cuenta")
    void cancelarEnMovimiento() {
        service.iniciar();
        String viejo = paso(1).commandId();

        service.cancelar();
        assertThat(paso(1).estado()).isEqualTo("OMITIDO");
        evento(viejo, "LLEGO");   // llegó tarde: el paso en curso es el HOME
        assertThat(paso(2).estado()).isEqualTo("OMITIDO");
        assertThat(pasada().estado()).isEqualTo("EN_CURSO");

        evento(paso(5).commandId(), "ERROR", "HOME_NO_ENCONTRADO", "x");

        assertThat(pasada().estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancelar estando en HOME no lo reenvía: espera a que termine")
    void cancelarEstandoEnHome() {
        service.iniciar();
        evento(paso(1).commandId(), "ERROR", "FIN_DE_CARRERA", "x");

        service.cancelar();

        verify(riel, times(1)).home();
        llego(5);
        assertThat(pasada().estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancelar sin pasada en curso: 409")
    void cancelarSinPasada() {
        assertThatThrownBy(() -> service.cancelar())
                .isInstanceOf(PasadaRechazadaException.class)
                .hasMessage("No hay una pasada en curso.");

        service.iniciar();
        service.cancelar();
        llego(5);
        assertThatThrownBy(() -> service.cancelar()).isInstanceOf(PasadaRechazadaException.class);
    }

    @Test
    @DisplayName("cancelar dos veces no manda dos HOME")
    void cancelarDosVeces() {
        service.iniciar();
        service.cancelar();

        assertThatThrownBy(() -> service.cancelar()).isInstanceOf(PasadaRechazadaException.class);
        verify(riel, times(1)).home();
    }

    // ---------------------------------------------------------------- estado()

    @Test
    @DisplayName("estado() es vacío si nunca hubo una pasada")
    void estadoSinPasada() {
        assertThat(service.estado()).isEmpty();
    }

    @Test
    @DisplayName("estado() completa el diagnóstico desde el repositorio aun con la pasada terminada")
    void estadoCompletaDiagnostico() {
        iniciarYLlegarAPos1();
        foto(2, "CAP-000001");
        llego(3);
        foto(4, "CAP-000002");
        llego(5);
        assertThat(pasada().estado()).isEqualTo("COMPLETADA");
        assertThat(paso(2).diagnostico()).isNull();

        when(diagRepo.findFirstByCapturaIdOrderByCreadoEnDesc("CAP-000001")).thenReturn(Optional.of(
                new DiagnosticoEntity("D1", "MZ-2-001", "MZ-2", "CAP-000001", "Clorosis", 87.0, "Media", 1759514530000L)));

        PasoPasada p2 = paso(2);
        assertThat(p2.diagnostico()).isNotNull();
        assertThat(p2.diagnostico().estado()).isEqualTo("Clorosis");
        assertThat(p2.diagnostico().conf()).isEqualTo(87.0);
        assertThat(p2.diagnostico().sev()).isEqualTo("Media");
        assertThat(p2.diagnostico().creadoEn()).isEqualTo(1759514530000L);
        assertThat(paso(4).diagnostico()).isNull();
        verify(diagRepo, never()).findFirstByCapturaIdOrderByCreadoEnDesc(eq("CAP-000009"));
    }
}
