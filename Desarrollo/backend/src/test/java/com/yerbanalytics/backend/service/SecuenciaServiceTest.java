package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.config.SecuenciaProperties;
import com.yerbanalytics.backend.dto.IniciarSecuencia;
import com.yerbanalytics.backend.dto.ParametrosSecuencia;
import com.yerbanalytics.backend.dto.PasoSecuencia;
import com.yerbanalytics.backend.dto.Secuencia;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher.Resultado;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.AckActuador;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las tres secuencias guionadas de la Demo Expo (design add-secuencias-demo-expo §2.3): la máquina de
 * pasos con el reloj y los publicadores controlados por el test. Cada {@code tick()} avanza como mucho
 * un paso por secuencia, igual que en producción.
 */
@DisplayName("SecuenciaService")
class SecuenciaServiceTest {

    private static final String ZONA = "MZ-2";
    private static final String SECTOR = "MZ-2-001";

    private final ComandoActuadorPublisher actuadores = mock(ComandoActuadorPublisher.class);
    private final ComandoZonaPublisher zonaPublisher = mock(ComandoZonaPublisher.class);
    private final ZonaRepository zonaRepo = mock(ZonaRepository.class);
    private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-10-10T15:00:00Z"));
    private final AtomicInteger seq = new AtomicInteger();
    private final ObjectMapper json = new ObjectMapper();
    /** Lo que dice la otra parte del guardia (la pasada): vacío = libre. */
    private final AtomicReference<Optional<String>> pasadaOcupada = new AtomicReference<>(Optional.empty());
    private SecuenciaService service;

    @BeforeEach
    void preparar() {
        // MZ-10 y MZ-2 desordenadas (lexicográficamente ganaría MZ-10) y sectores fuera de orden.
        when(zonaRepo.findAllWithSectors()).thenReturn(List.of(
                zona("MZ-10", "MZ-10-001"),
                zona(ZONA, "MZ-2-003", SECTOR, "MZ-2-002")));
        when(actuadores.publicar(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(i -> new Resultado(true, "cmd-" + seq.incrementAndGet(), null));
        when(zonaPublisher.leerAhora(anyString()))
                .thenAnswer(i -> new ComandoZonaPublisher.Resultado(true, "lec-" + seq.incrementAndGet(), null));
        service = new SecuenciaService(actuadores, zonaPublisher, zonaRepo,
                GuardiaHardwareTest.guardiaCon(() -> pasadaOcupada.get()),
                new SecuenciaProperties(), reloj);
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

    private Secuencia iniciar(String tipo, Integer duracionSeg, Integer esperaSeg) {
        return service.iniciar(new IniciarSecuencia(tipo, new ParametrosSecuencia(duracionSeg, esperaSeg)));
    }

    private Secuencia secuencia() {
        return service.estado().orElseThrow();
    }

    private PasoSecuencia paso(int n) {
        return secuencia().pasos().get(n - 1);
    }

    private void avanzar(int segundos) {
        reloj.avanzar(Duration.ofSeconds(segundos));
        service.tick();
    }

    private void ack(String commandId, String status, String detalleJson) {
        try {
            JsonNode detalle = detalleJson == null ? null : json.readTree(detalleJson);
            service.registrarAck(new AckActuador(commandId, status, detalle, ZONA, SECTOR));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        service.tick();
    }

    private void ackOk(int n) {
        ack(paso(n).commandId(), "SUCCESS", "{\"tipo\":\"ok\"}");
    }

    private static MqttTelemetryPayload telemetria(Double ceUsCm) {
        return new MqttTelemetryPayload("AA:BB", null, -60, 812L,
                new MqttTelemetryPayload.MetricsPayload(41.0, 63.0, 22.5, null, 78.0, ceUsCm, null, null, null, null));
    }

    private void verificarPublicado(String actuador, String accion, Map<String, Object> parametros) {
        verify(actuadores).publicar(ZONA, SECTOR, actuador, accion, parametros);
    }

    // ================================================================ RIEGO

    @Nested
    @DisplayName("riego")
    class Riego {

        @Test
        @DisplayName("iniciar abre la válvula del primer sector de la zona de menor número, con durationSec = N + timeout del ACK")
        void iniciaConElDestinoYLaRedDeSeguridad() {
            Secuencia s = iniciar("RIEGO", 15, null);

            assertThat(s.tipo()).isEqualTo("RIEGO");
            assertThat(s.estado()).isEqualTo("EN_CURSO");
            assertThat(s.zonaId()).isEqualTo(ZONA);
            assertThat(s.sectorId()).isEqualTo(SECTOR);
            assertThat(s.parametros()).isEqualTo(new ParametrosSecuencia(15, null));
            assertThat(s.pasos()).extracting(PasoSecuencia::tipo).containsExactly("ABRIR", "ESPERAR", "CERRAR");
            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("EN_CURSO", "PENDIENTE", "PENDIENTE");
            assertThat(s.pasos().get(0).commandId()).isEqualTo("cmd-1");
            assertThat(s.iniciadaEn()).isEqualTo(reloj.millis());
            verificarPublicado("valve", "ON", Map.of("durationSec", 25));
        }

        @Test
        @DisplayName("sin parámetros usa 10 s por defecto")
        void duracionPorDefecto() {
            Secuencia s = service.iniciar(new IniciarSecuencia("RIEGO", null));

            assertThat(s.parametros().duracionSeg()).isEqualTo(10);
            verificarPublicado("valve", "ON", Map.of("durationSec", 20));
        }

        @Test
        @DisplayName("ACK SUCCESS pasa a ESPERAR con esperaHasta; a los N s cierra con OFF y el ACK completa")
        void caminoFeliz() {
            iniciar("RIEGO", 15, null);
            long t0 = reloj.millis();

            ackOk(1);
            assertThat(paso(1).estado()).isEqualTo("OK");
            assertThat(paso(2).estado()).isEqualTo("EN_CURSO");
            assertThat(paso(2).esperaHasta()).isEqualTo(t0 + 15_000);

            avanzar(14);
            verify(actuadores, never()).publicar(anyString(), anyString(), eq("valve"), eq("OFF"), anyMap());
            assertThat(paso(2).estado()).isEqualTo("EN_CURSO");

            avanzar(1);
            assertThat(paso(2).estado()).isEqualTo("OK");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("valve", "OFF", Map.of());

            ackOk(3);
            Secuencia s = secuencia();
            assertThat(s.estado()).isEqualTo("COMPLETADA");
            assertThat(s.finalizadaEn()).isEqualTo(reloj.millis());
            assertThat(s.error()).isNull();
            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("OK", "OK", "OK");
        }

        @Test
        @DisplayName("un ACK con commandId ajeno (del motor, por ejemplo) se ignora")
        void ackAjenoSeIgnora() {
            iniciar("RIEGO", 15, null);

            ack("de-otro-comando", "SUCCESS", "{\"tipo\":\"ok\"}");

            assertThat(paso(1).estado()).isEqualTo("EN_CURSO");
        }

        @Test
        @DisplayName("ABRIR sin ACK a los 10 s: ACTUADOR_SIN_RESPUESTA, ESPERAR omitido, el OFF se publica igual y termina FALLIDA")
        void abrirSinAck() {
            iniciar("RIEGO", 15, null);

            avanzar(9);
            assertThat(paso(1).estado()).isEqualTo("EN_CURSO");

            avanzar(1);
            assertThat(paso(1).estado()).isEqualTo("ERROR");
            assertThat(paso(1).codigoError()).isEqualTo("ACTUADOR_SIN_RESPUESTA");
            assertThat(paso(1).detalle()).isEqualTo(
                    "La válvula no respondió en 10 s. ¿El ESP32 está encendido y conectado al broker?");
            assertThat(paso(2).estado()).isEqualTo("OMITIDO");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("valve", "OFF", Map.of());

            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("FALLIDA");
            assertThat(secuencia().error()).startsWith("La válvula no respondió");
        }

        @Test
        @DisplayName("ACK ERROR en ABRIR: el código sale de detalle.tipo y el OFF se publica igual")
        void ackErrorEnAbrir() {
            iniciar("RIEGO", 15, null);

            ack(paso(1).commandId(), "ERROR", "{\"tipo\":\"duracion_invalida\"}");

            assertThat(paso(1).estado()).isEqualTo("ERROR");
            assertThat(paso(1).codigoError()).isEqualTo("DURACION_INVALIDA");
            assertThat(paso(1).detalle()).isEqualTo("El ESP32 rechazó el comando (duracion_invalida).");
            assertThat(paso(2).estado()).isEqualTo("OMITIDO");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("valve", "OFF", Map.of());
        }

        @Test
        @DisplayName("ACK ERROR de un tipo desconocido: texto genérico con el tipo")
        void ackErrorDesconocido() {
            iniciar("RIEGO", 15, null);

            ack(paso(1).commandId(), "ERROR", "{\"tipo\":\"rayo_cosmico\"}");

            assertThat(paso(1).codigoError()).isEqualTo("RAYO_COSMICO");
            assertThat(paso(1).detalle()).isEqualTo("El actuador informó un error: rayo_cosmico");
        }

        @Test
        @DisplayName("CERRAR sin ACK: FALLIDA, y el detalle avisa que el nodo apaga solo a los durationSec")
        void cerrarSinAck() {
            iniciar("RIEGO", 15, null);
            ackOk(1);
            avanzar(15);

            avanzar(10);

            assertThat(paso(3).estado()).isEqualTo("ERROR");
            assertThat(paso(3).codigoError()).isEqualTo("ACTUADOR_SIN_RESPUESTA");
            assertThat(paso(3).detalle()).contains("La válvula no respondió en 10 s")
                    .endsWith("El nodo apaga la bomba solo a los 25 s.");
            assertThat(secuencia().estado()).isEqualTo("FALLIDA");
            // El paso seguro no se reintenta.
            verify(actuadores, times(1)).publicar(anyString(), anyString(), eq("valve"), eq("OFF"), anyMap());
        }

        @Test
        @DisplayName("publicación fallida de ABRIR: PUBLICACION_FALLIDA al instante y se intenta cerrar igual")
        void publicacionFallidaAlAbrir() {
            when(actuadores.publicar(anyString(), anyString(), eq("valve"), eq("ON"), anyMap()))
                    .thenReturn(new Resultado(false, "x", "broker caído"));

            Secuencia s = iniciar("RIEGO", 15, null);

            assertThat(s.pasos().get(0).codigoError()).isEqualTo("PUBLICACION_FALLIDA");
            assertThat(s.pasos().get(0).detalle()).isEqualTo("No se pudo publicar el comando al broker: broker caído");
            assertThat(s.pasos().get(1).estado()).isEqualTo("OMITIDO");
            assertThat(s.pasos().get(2).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("valve", "OFF", Map.of());
        }

        @Test
        @DisplayName("publicación fallida de CERRAR: FALLIDA con PUBLICACION_FALLIDA")
        void publicacionFallidaAlCerrar() {
            iniciar("RIEGO", 15, null);
            ackOk(1);
            when(actuadores.publicar(anyString(), anyString(), eq("valve"), eq("OFF"), anyMap()))
                    .thenReturn(new Resultado(false, "x", "broker caído"));

            avanzar(15);

            assertThat(paso(3).codigoError()).isEqualTo("PUBLICACION_FALLIDA");
            assertThat(paso(3).detalle()).contains("broker caído").contains("El nodo apaga la bomba solo a los 25 s.");
            assertThat(secuencia().estado()).isEqualTo("FALLIDA");
        }

        @Test
        @DisplayName("cancelar en la espera: ESPERAR omitido, OFF, y al ACK queda CANCELADA")
        void cancelarEnLaEspera() {
            iniciar("RIEGO", 15, null);
            ackOk(1);
            avanzar(3);

            Secuencia s = service.cancelar();

            assertThat(s.cancelacionSolicitada()).isTrue();
            assertThat(s.estado()).isEqualTo("EN_CURSO");
            assertThat(s.pasos().get(1).estado()).isEqualTo("OMITIDO");
            assertThat(s.pasos().get(1).detalle()).isEqualTo("Cancelada por el operador");
            assertThat(s.pasos().get(2).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("valve", "OFF", Map.of());

            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("CANCELADA");
            assertThat(secuencia().finalizadaEn()).isNotNull();
        }

        @Test
        @DisplayName("cancelar mientras espera el ACK de ABRIR: ABRIR omitido, OFF, y el ACK viejo ya no cuenta")
        void cancelarEnAbrir() {
            iniciar("RIEGO", 15, null);
            String viejo = paso(1).commandId();

            service.cancelar();
            assertThat(paso(1).estado()).isEqualTo("OMITIDO");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");

            ack(viejo, "SUCCESS", "{\"tipo\":\"ok\"}");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("CANCELADA");
        }

        @Test
        @DisplayName("cancelar sin secuencia, dos veces o ya terminada: 409")
        void cancelarRechazos() {
            assertThatThrownBy(() -> service.cancelar())
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessage("No hay una secuencia en curso.");

            iniciar("RIEGO", 15, null);
            service.cancelar();
            assertThatThrownBy(() -> service.cancelar()).isInstanceOf(SecuenciaRechazadaException.class);
            verify(actuadores, times(1)).publicar(anyString(), anyString(), eq("valve"), eq("OFF"), anyMap());

            ackOk(3);
            assertThatThrownBy(() -> service.cancelar())
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessage("No hay una secuencia en curso.");
        }

        @Test
        @DisplayName("400: tipo desconocido, duracionSeg < 1 o mayor que el tope (1200 - timeout del ACK); nada se publica")
        void validacion() {
            assertThatThrownBy(() -> iniciar("LLUVIA", null, null)).isInstanceOf(SecuenciaInvalidaException.class);
            assertThatThrownBy(() -> iniciar(null, null, null)).isInstanceOf(SecuenciaInvalidaException.class);
            assertThatThrownBy(() -> iniciar("RIEGO", 0, null)).isInstanceOf(SecuenciaInvalidaException.class);
            assertThatThrownBy(() -> iniciar("RIEGO", -5, null)).isInstanceOf(SecuenciaInvalidaException.class);
            assertThatThrownBy(() -> iniciar("RIEGO", 1191, null))
                    .isInstanceOf(SecuenciaInvalidaException.class)
                    .hasMessageContaining("1190");

            verifyNoInteractions(actuadores);
            assertThat(service.estado()).isEmpty();
        }

        @Test
        @DisplayName("los topes se aceptan: 1 y 1190 s")
        void topesValidos() {
            iniciar("RIEGO", 1, null);
            verificarPublicado("valve", "ON", Map.of("durationSec", 11));
            service.cancelar();
            ackOk(3);

            iniciar("RIEGO", 1190, null);
            verificarPublicado("valve", "ON", Map.of("durationSec", 1200));
        }

        @Test
        @DisplayName("409 si ya hay una secuencia en curso, sin publicar de nuevo")
        void unaALaVez() {
            iniciar("RIEGO", 15, null);

            assertThatThrownBy(() -> iniciar("MEDIASOMBRA", null, 5))
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessage("Ya hay una secuencia en curso.");
            verify(actuadores, times(1)).publicar(anyString(), anyString(), anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("409 con el motivo del guardia si hay una pasada en curso, y no publica nada")
        void rechazaConUnaPasadaEnCurso() {
            pasadaOcupada.set(Optional.of("Hay una pasada del riel en curso."));

            assertThatThrownBy(() -> iniciar("RIEGO", 15, null))
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessage("Hay una pasada del riel en curso.");
            verifyNoInteractions(actuadores);
            assertThat(service.estado()).isEmpty();
        }

        @Test
        @DisplayName("409 sin topología, y 409 si la zona no tiene sectores")
        void topologia() {
            when(zonaRepo.findAllWithSectors()).thenReturn(List.of());
            assertThatThrownBy(() -> iniciar("RIEGO", 15, null))
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessage("No hay topología configurada.");

            when(zonaRepo.findAllWithSectors()).thenReturn(List.of(zona("MZ-1")));
            assertThatThrownBy(() -> iniciar("RIEGO", 15, null))
                    .isInstanceOf(SecuenciaRechazadaException.class)
                    .hasMessageContaining("MZ-1");
            verifyNoInteractions(actuadores);
        }

        @Test
        @DisplayName("ocupadoPor() informa ocupado sólo con la secuencia EN_CURSO")
        void ocupadoPor() {
            assertThat(service.ocupadoPor()).isEmpty();

            iniciar("RIEGO", 15, null);
            assertThat(service.ocupadoPor()).contains("Hay una secuencia de riego en curso.");

            service.cancelar();
            ackOk(3);
            assertThat(service.ocupadoPor()).isEmpty();
        }

        @Test
        @DisplayName("terminada una secuencia se puede iniciar otra, y estado() devuelve la nueva")
        void sePuedeIniciarOtra() {
            Secuencia primera = iniciar("RIEGO", 15, null);
            service.cancelar();
            ackOk(3);

            Secuencia segunda = iniciar("RIEGO", 5, null);

            assertThat(segunda.id()).isNotEqualTo(primera.id());
            assertThat(secuencia().id()).isEqualTo(segunda.id());
        }

        @Test
        @DisplayName("estado() es vacío si nunca hubo una secuencia")
        void estadoSinSecuencia() {
            assertThat(service.estado()).isEmpty();
        }
    }

    // ================================================================ MEDIASOMBRA

    @Nested
    @DisplayName("mediasombra")
    class Mediasombra {

        @Test
        @DisplayName("SET 0, espera, SET 100 y COMPLETADA")
        void caminoFeliz() {
            Secuencia s = iniciar("MEDIASOMBRA", null, 7);

            assertThat(s.parametros()).isEqualTo(new ParametrosSecuencia(null, 7));
            assertThat(s.sectorId()).isEqualTo(SECTOR);
            assertThat(s.pasos()).extracting(PasoSecuencia::tipo).containsExactly("DESPLEGAR", "ESPERAR", "ENROLLAR");
            verificarPublicado("shade", "SET", Map.of("targetPct", 0));

            long t0 = reloj.millis();
            ackOk(1);
            assertThat(paso(2).esperaHasta()).isEqualTo(t0 + 7_000);
            avanzar(7);
            verificarPublicado("shade", "SET", Map.of("targetPct", 100));

            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("COMPLETADA");
        }

        @Test
        @DisplayName("sin parámetros espera 10 s por defecto")
        void esperaPorDefecto() {
            assertThat(service.iniciar(new IniciarSecuencia("MEDIASOMBRA", null)).parametros().esperaSeg())
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("esperaSeg 0 es válida: pasa a ENROLLAR en el tick siguiente")
        void esperaCero() {
            iniciar("MEDIASOMBRA", null, 0);
            ackOk(1);

            service.tick();

            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("shade", "SET", Map.of("targetPct", 100));
        }

        @Test
        @DisplayName("ACK ERROR falla_mecanica en DESPLEGAR: FALLA_MECANICA, ENROLLAR igual, FALLIDA")
        void fallaMecanicaEnDesplegar() {
            iniciar("MEDIASOMBRA", null, 7);

            ack(paso(1).commandId(), "ERROR", "{\"tipo\":\"falla_mecanica\"}");

            assertThat(paso(1).codigoError()).isEqualTo("FALLA_MECANICA");
            assertThat(paso(1).detalle()).isEqualTo("La mediasombra no llegó al final de carrera.");
            assertThat(paso(2).estado()).isEqualTo("OMITIDO");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
            verificarPublicado("shade", "SET", Map.of("targetPct", 100));

            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("FALLIDA");
            assertThat(secuencia().error()).isEqualTo("La mediasombra no llegó al final de carrera.");
        }

        @Test
        @DisplayName("REEMPLAZADO: texto de movimiento interrumpido")
        void reemplazado() {
            iniciar("MEDIASOMBRA", null, 7);

            ack(paso(1).commandId(), "ERROR", "{\"tipo\":\"reemplazado\"}");

            assertThat(paso(1).codigoError()).isEqualTo("REEMPLAZADO");
            assertThat(paso(1).detalle()).isEqualTo("El movimiento fue interrumpido por otro comando.");
        }

        @Test
        @DisplayName("sin ACK la mediasombra tiene 45 s, no 10")
        void timeoutDeLaMediasombra() {
            iniciar("MEDIASOMBRA", null, 7);

            avanzar(44);
            assertThat(paso(1).estado()).isEqualTo("EN_CURSO");

            avanzar(1);
            assertThat(paso(1).codigoError()).isEqualTo("ACTUADOR_SIN_RESPUESTA");
            assertThat(paso(1).detalle()).startsWith("La mediasombra no respondió en 45 s.");
            assertThat(paso(3).estado()).isEqualTo("EN_CURSO");
        }

        @Test
        @DisplayName("cancelar mientras se despliega: ENROLLAR ocupa su lugar y termina CANCELADA")
        void cancelarEnDesplegar() {
            iniciar("MEDIASOMBRA", null, 7);

            Secuencia s = service.cancelar();

            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("OMITIDO", "OMITIDO", "EN_CURSO");
            verificarPublicado("shade", "SET", Map.of("targetPct", 100));
            ackOk(3);
            assertThat(secuencia().estado()).isEqualTo("CANCELADA");
        }

        @Test
        @DisplayName("400: esperaSeg fuera de 0..600")
        void validacion() {
            assertThatThrownBy(() -> iniciar("MEDIASOMBRA", null, -1)).isInstanceOf(SecuenciaInvalidaException.class);
            assertThatThrownBy(() -> iniciar("MEDIASOMBRA", null, 601)).isInstanceOf(SecuenciaInvalidaException.class);
            verifyNoInteractions(actuadores);

            iniciar("MEDIASOMBRA", null, 600);
            assertThat(secuencia().parametros().esperaSeg()).isEqualTo(600);
        }
    }

    // ================================================================ LECTURA

    @Nested
    @DisplayName("lectura")
    class Lectura {

        @Test
        @DisplayName("iniciar pide la lectura a la zona (sin sector) y queda esperando la telemetría")
        void inicia() {
            Secuencia s = iniciar("LECTURA", null, null);

            assertThat(s.zonaId()).isEqualTo(ZONA);
            assertThat(s.sectorId()).isNull();
            assertThat(s.parametros()).isEqualTo(new ParametrosSecuencia(null, null));
            assertThat(s.pasos()).extracting(PasoSecuencia::tipo).containsExactly("PEDIR", "ESPERAR_TELEMETRIA", "MOSTRAR");
            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("OK", "EN_CURSO", "PENDIENTE");
            assertThat(s.pasos().get(0).commandId()).isEqualTo("lec-1");
            verify(zonaPublisher).leerAhora(ZONA);
            verifyNoInteractions(actuadores);
        }

        @Test
        @DisplayName("la telemetría de otra zona se ignora")
        void otraZona() {
            iniciar("LECTURA", null, null);

            service.registrarTelemetria("MZ-3", telemetria(1200.0), reloj.millis());
            service.tick();

            assertThat(paso(2).estado()).isEqualTo("EN_CURSO");
            assertThat(secuencia().lectura()).isNull();
        }

        @Test
        @DisplayName("una lectura recibida antes del pedido se ignora")
        void anteriorAlPedido() {
            reloj.avanzar(Duration.ofSeconds(5));
            iniciar("LECTURA", null, null);

            service.registrarTelemetria(ZONA, telemetria(1200.0), reloj.millis() - 1);
            service.tick();

            assertThat(paso(2).estado()).isEqualTo("EN_CURSO");
            assertThat(secuencia().lectura()).isNull();
        }

        @Test
        @DisplayName("la primera lectura posterior completa: ce en dS/m, las 10 claves, MOSTRAR OK")
        void caminoFeliz() {
            iniciar("LECTURA", null, null);
            reloj.avanzar(Duration.ofSeconds(3));

            service.registrarTelemetria(ZONA, telemetria(1200.0), reloj.millis());
            service.registrarTelemetria(ZONA, telemetria(9999.0), reloj.millis());   // la segunda no cuenta
            service.tick();

            Secuencia s = secuencia();
            assertThat(s.estado()).isEqualTo("COMPLETADA");
            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("OK", "OK", "OK");
            assertThat(s.lectura().recibidaEn()).isEqualTo(reloj.millis());
            assertThat(s.lectura().metricas()).containsOnlyKeys(
                    "humSus", "humAmb", "temp", "tempSuelo", "uv", "ce", "phSuelo", "n", "p", "k");
            assertThat(s.lectura().metricas().get("ce")).isEqualTo(1.2);
            assertThat(s.lectura().metricas().get("uv")).isEqualTo(78.0);
            assertThat(s.lectura().metricas().get("humSus")).isEqualTo(41.0);
            assertThat(s.lectura().metricas().get("tempSuelo")).isNull();
            assertThat(s.finalizadaEn()).isEqualTo(reloj.millis());
        }

        @Test
        @DisplayName("una lectura sin métricas igual completa, con todo en null")
        void sinMetricas() {
            iniciar("LECTURA", null, null);

            service.registrarTelemetria(ZONA, new MqttTelemetryPayload("AA", null, null, null, null), reloj.millis());
            service.tick();

            assertThat(secuencia().estado()).isEqualTo("COMPLETADA");
            assertThat(secuencia().lectura().metricas().values()).containsOnlyNulls();
        }

        @Test
        @DisplayName("sin lectura a los 20 s: SIN_LECTURA, MOSTRAR omitido y FALLIDA")
        void sinLectura() {
            iniciar("LECTURA", null, null);

            avanzar(19);
            assertThat(paso(2).estado()).isEqualTo("EN_CURSO");

            avanzar(1);
            assertThat(paso(2).estado()).isEqualTo("ERROR");
            assertThat(paso(2).codigoError()).isEqualTo("SIN_LECTURA");
            assertThat(paso(2).detalle()).isEqualTo(
                    "La zona MZ-2 no publicó una lectura en 20 s. ¿El nodo tiene sensores configurados?");
            assertThat(paso(3).estado()).isEqualTo("OMITIDO");
            assertThat(secuencia().estado()).isEqualTo("FALLIDA");
            assertThat(secuencia().error()).startsWith("La zona MZ-2 no publicó");
        }

        @Test
        @DisplayName("publicación fallida de PEDIR: PUBLICACION_FALLIDA y FALLIDA al instante")
        void publicacionFallida() {
            when(zonaPublisher.leerAhora(anyString()))
                    .thenReturn(new ComandoZonaPublisher.Resultado(false, "x", "broker caído"));

            Secuencia s = iniciar("LECTURA", null, null);

            assertThat(s.estado()).isEqualTo("FALLIDA");
            assertThat(s.pasos().get(0).codigoError()).isEqualTo("PUBLICACION_FALLIDA");
            assertThat(s.pasos().get(1).estado()).isEqualTo("OMITIDO");
            assertThat(s.pasos().get(2).estado()).isEqualTo("OMITIDO");
        }

        @Test
        @DisplayName("cancelar: CANCELADA al instante, sin nada que dejar seguro")
        void cancelar() {
            iniciar("LECTURA", null, null);

            Secuencia s = service.cancelar();

            assertThat(s.estado()).isEqualTo("CANCELADA");
            assertThat(s.cancelacionSolicitada()).isTrue();
            assertThat(s.finalizadaEn()).isNotNull();
            assertThat(s.pasos()).extracting(PasoSecuencia::estado).containsExactly("OK", "OMITIDO", "OMITIDO");
            verifyNoInteractions(actuadores);
            assertThat(service.ocupadoPor()).isEmpty();
        }

        @Test
        @DisplayName("una lectura que llega tras cancelar no resucita la secuencia")
        void lecturaTardia() {
            iniciar("LECTURA", null, null);
            service.cancelar();

            service.registrarTelemetria(ZONA, telemetria(1200.0), reloj.millis());
            service.tick();

            assertThat(secuencia().estado()).isEqualTo("CANCELADA");
            assertThat(secuencia().lectura()).isNull();
        }
    }
}
