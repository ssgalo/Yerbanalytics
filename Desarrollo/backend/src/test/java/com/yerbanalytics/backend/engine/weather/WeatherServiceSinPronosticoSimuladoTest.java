package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Modo demo "sin pronóstico": el servicio responde como si Open-Meteo estuviera caída (O-01). */
@DisplayName("WeatherService - sin pronóstico simulado")
class WeatherServiceSinPronosticoSimuladoTest {

    private WeatherService servicio;

    private static WeatherForecast pronostico(Instant ahora) {
        return new WeatherForecast(10, 5, ahora, 22.0, "Despejado", 60.0, List.of());
    }

    @BeforeEach
    void setUp() {
        var reloj = new RelojDePrueba(Instant.parse("2026-10-05T13:05:00Z"));
        servicio = new WeatherService(() -> pronostico(reloj.instant()), 900_000L, 1, 0, 60_000L, reloj, Runnable::run);
    }

    @Test
    void sinPronosticoSimuladoElServicioResponde() {
        assertThat(servicio.isOfflineSimulated()).isFalse();
        assertThat(servicio.getForecast()).isNotNull();
        assertThat(servicio.getForecastSinEspera()).isNotNull();
    }

    @Test
    @DisplayName("con el modo offline ambas consultas devuelven null")
    void offlineDevuelveNull() {
        servicio.setOfflineSimulated(true);

        assertThat(servicio.isOfflineSimulated()).isTrue();
        assertThat(servicio.getForecast()).isNull();
        assertThat(servicio.getForecastSinEspera()).isNull();
    }

    @Test
    @DisplayName("el modo offline gana sobre un pronóstico ficticio inyectado")
    void offlineGanaSobreElFicticio() {
        servicio.setMockForecast(pronostico(Instant.parse("2026-10-05T13:05:00Z")));
        servicio.setOfflineSimulated(true);

        assertThat(servicio.getForecast()).isNull();
        assertThat(servicio.getForecastSinEspera()).isNull();
    }

    @Test
    @DisplayName("apagar el modo offline restablece el pronóstico")
    void apagarRestablece() {
        servicio.setOfflineSimulated(true);
        servicio.setOfflineSimulated(false);

        assertThat(servicio.getForecast()).isNotNull();
    }

    @Test
    @DisplayName("borrar el clima simulado también apaga el modo offline")
    void borrarLimpiaElOffline() {
        servicio.setMockForecast(pronostico(Instant.parse("2026-10-05T13:05:00Z")));
        servicio.setOfflineSimulated(true);

        servicio.clearMockForecast();

        assertThat(servicio.isOfflineSimulated()).isFalse();
        assertThat(servicio.getMockForecast()).isNull();
        assertThat(servicio.getForecast()).isNotNull();
    }
}
