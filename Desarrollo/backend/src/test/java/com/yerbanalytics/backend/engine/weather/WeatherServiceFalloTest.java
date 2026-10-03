package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El pronóstico no puede trabar el hilo de la telemetría: con la API caída el fallo se cachea (no se reintenta
 * por cada mensaje ni por cada sector) y quien evalúa por telemetría nunca espera ni duerme.
 */
@DisplayName("WeatherService - fallo cacheado y consulta sin espera")
class WeatherServiceFalloTest {

    private static final Instant T0 = Instant.parse("2026-10-05T13:05:00Z");
    private static final long TTL = 900_000L;
    private static final long TTL_FALLO = 60_000L;

    private RelojDePrueba reloj;
    private final AtomicInteger llamadas = new AtomicInteger();
    private volatile boolean apiCaida;
    /** Las tareas asincrónicas quedan en una lista: el test decide cuándo corren. */
    private final List<Runnable> pendientes = new ArrayList<>();
    private WeatherService servicio;

    private WeatherForecast pronostico() {
        return new WeatherForecast(10, 5, reloj.instant(), 22.0, "Despejado", 60.0, List.of());
    }

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(T0);
        WeatherClient cliente = () -> {
            llamadas.incrementAndGet();
            return apiCaida ? null : pronostico();
        };
        Executor aparte = pendientes::add;
        // retry-base 0: el test no duerme (los reintentos con espera sólo corren fuera del hilo de telemetría)
        servicio = new WeatherService(cliente, TTL, 3, 0, TTL_FALLO, reloj, aparte);
    }

    private void correrPendientes() {
        List<Runnable> copia = new ArrayList<>(pendientes);
        pendientes.clear();
        copia.forEach(Runnable::run);
    }

    @Test
    @DisplayName("con la API caída, getForecast() reintenta UNA vez y durante el TTL del fallo no vuelve a llamar")
    void falloCacheado() {
        apiCaida = true;

        assertThat(servicio.getForecast()).isNull();
        assertThat(llamadas.get()).isEqualTo(3);                  // los 3 reintentos de la primera vez

        for (int i = 0; i < 100; i++) {                           // 100 sectores / mensajes
            assertThat(servicio.getForecast()).isNull();
        }
        assertThat(llamadas.get()).as("el fallo está cacheado").isEqualTo(3);

        reloj.avanzar(Duration.ofSeconds(61));
        servicio.getForecast();
        assertThat(llamadas.get()).as("vencido el TTL del fallo se vuelve a intentar").isEqualTo(6);
    }

    @Test
    @DisplayName("un pronóstico nuevo borra el fallo cacheado")
    void exitoLimpiaElFallo() {
        apiCaida = true;
        servicio.getForecast();
        reloj.avanzar(Duration.ofSeconds(61));
        apiCaida = false;

        assertThat(servicio.getForecast()).isNotNull();
        assertThat(servicio.getForecast()).isNotNull();
        assertThat(llamadas.get()).isEqualTo(3 + 1);              // el éxito se cachea: una sola llamada más
    }

    @Test
    @DisplayName("sin espera: sin pronóstico cacheado devuelve null YA, y lo pide aparte (una sola vez aunque lleguen 100 mensajes)")
    void sinEsperaNoLlamaEnElHiloLlamador() {
        for (int i = 0; i < 100; i++) {
            assertThat(servicio.getForecastSinEspera()).isNull();
        }

        assertThat(llamadas.get()).as("el hilo de telemetría no consulta la API").isZero();
        assertThat(pendientes).as("una sola consulta en vuelo").hasSize(1);

        correrPendientes();
        assertThat(llamadas.get()).isEqualTo(1);
        assertThat(servicio.getForecastSinEspera()).as("ya cacheado").isNotNull();
        assertThat(pendientes).isEmpty();
    }

    @Test
    @DisplayName("sin espera con la API caída: null, UN intento aparte y el fallo cacheado (no se pide en cada mensaje)")
    void sinEsperaConApiCaida() {
        apiCaida = true;
        servicio.getForecastSinEspera();
        correrPendientes();
        assertThat(llamadas.get()).isEqualTo(3);

        for (int i = 0; i < 100; i++) {
            assertThat(servicio.getForecastSinEspera()).isNull();
        }

        assertThat(pendientes).as("no se vuelve a pedir durante el TTL del fallo").isEmpty();
        assertThat(llamadas.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("pronóstico vencido pero usable: se sigue usando mientras se refresca aparte")
    void staleWhileRevalidate() {
        servicio.getForecast();                                    // cachea uno a las T0
        reloj.avanzar(Duration.ofMillis(TTL + 1));                 // venció el TTL (15 min)

        WeatherForecast f = servicio.getForecastSinEspera();

        assertThat(f).as("el viejo, no null: R-03 no se queda sin dato cada 15 min").isNotNull();
        assertThat(pendientes).hasSize(1);
        correrPendientes();
        assertThat(servicio.getForecastSinEspera().timestamp()).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("un pronóstico demasiado viejo (4 TTL) ya no se usa: se evalúa sin pronóstico")
    void demasiadoViejoNoSeUsa() {
        servicio.getForecast();
        reloj.avanzar(Duration.ofMillis(4 * TTL + 1));
        apiCaida = true;

        assertThat(servicio.getForecastSinEspera()).isNull();
    }

    @Test
    @DisplayName("si la tarea asincrónica explota no se rompe nada y se puede volver a pedir")
    void tareaQueFalla() {
        WeatherClient roto = () -> {
            throw new IllegalStateException("boom");
        };
        servicio = new WeatherService(roto, TTL, 3, 0, TTL_FALLO, reloj, pendientes::add);

        assertThat(servicio.getForecastSinEspera()).isNull();
        correrPendientes();
        reloj.avanzar(Duration.ofSeconds(61));

        assertThat(servicio.getForecastSinEspera()).isNull();
        assertThat(pendientes).hasSize(1);
    }

    @Test
    @DisplayName("precalentar() pide el pronóstico aparte al arrancar: el primer mensaje del nodo ya lo encuentra cacheado")
    void precalentarPideElPronosticoAparte() {
        servicio.precalentar();

        assertThat(llamadas.get()).as("no llama en el hilo llamador").isZero();
        assertThat(pendientes).hasSize(1);
        correrPendientes();
        assertThat(llamadas.get()).isEqualTo(1);

        assertThat(servicio.getForecastSinEspera()).as("el primer mensaje ya tiene pronóstico").isNotNull();
        assertThat(pendientes).isEmpty();
    }

    @Test
    @DisplayName("precalentar() con la API caída no tira el arranque")
    void precalentarConLaApiCaidaNoFalla() {
        apiCaida = true;

        servicio.precalentar();
        correrPendientes();

        assertThat(servicio.getForecastSinEspera()).isNull();
    }
}
