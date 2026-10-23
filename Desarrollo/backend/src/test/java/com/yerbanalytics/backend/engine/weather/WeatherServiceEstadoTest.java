package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/** O-01…O-03: el pronóstico tiene tres estados con nombre, y el vencido no llega a las reglas. */
@DisplayName("WeatherService - estado del pronóstico")
class WeatherServiceEstadoTest {

    private static final Instant T0 = Instant.parse("2026-10-05T13:05:00Z");
    private static final long TTL = 900_000L;

    private RelojDePrueba reloj;
    private volatile boolean apiCaida;
    private WeatherService servicio;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(T0);
        WeatherClient cliente = () -> apiCaida ? null
                : new WeatherForecast(10, 5, reloj.instant(), 22.0, "Despejado", 60.0, List.of());
        Executor enElMismoHilo = Runnable::run;
        servicio = new WeatherService(cliente, TTL, 1, 0, 60_000L, reloj, enElMismoHilo);
    }

    @Test
    @DisplayName("nunca hubo pronóstico: ausente")
    void ausente() {
        apiCaida = true;
        servicio.getForecastSinEspera();

        assertThat(servicio.status()).isEqualTo(ForecastStatus.ABSENT);
    }

    @Test
    @DisplayName("hace 20 min: disponible, y las reglas lo reciben")
    void disponible() {
        servicio.getForecastSinEspera();
        reloj.avanzar(Duration.ofMinutes(20));
        apiCaida = true;

        assertThat(servicio.status()).isEqualTo(ForecastStatus.AVAILABLE);
        assertThat(servicio.getForecastSinEspera()).isNotNull();
    }

    @Test
    @DisplayName("hace 2 h: vencido, y las reglas actúan como si no hubiera pronóstico")
    void vencido() {
        servicio.getForecastSinEspera();
        reloj.avanzar(Duration.ofHours(2));
        apiCaida = true;

        assertThat(servicio.status()).isEqualTo(ForecastStatus.EXPIRED);
        assertThat(servicio.getForecastSinEspera()).isNull();
    }

    @Test
    @DisplayName("el límite es la vigencia utilizable (4 × el TTL de 15 min = 60 min), sin parámetro nuevo")
    void limiteDeVigencia() {
        servicio.getForecastSinEspera();
        apiCaida = true;

        reloj.avanzar(Duration.ofMinutes(59));
        assertThat(servicio.status()).isEqualTo(ForecastStatus.AVAILABLE);

        reloj.avanzar(Duration.ofMinutes(2));
        assertThat(servicio.status()).isEqualTo(ForecastStatus.EXPIRED);
    }

    @Test
    @DisplayName("el modo demo sin pronóstico es ausente aunque haya cache o pronóstico ficticio")
    void simuladoEsAusente() {
        servicio.getForecastSinEspera();
        servicio.setOfflineSimulated(true);

        assertThat(servicio.status()).isEqualTo(ForecastStatus.ABSENT);
    }

    @Test
    @DisplayName("un pronóstico ficticio inyectado cuenta como disponible")
    void ficticioEsDisponible() {
        apiCaida = true;
        servicio.setMockForecast(new WeatherForecast(80, 5, reloj.instant(), 22.0, "Lluvia", 60.0, List.of()));

        assertThat(servicio.status()).isEqualTo(ForecastStatus.AVAILABLE);
    }
}
