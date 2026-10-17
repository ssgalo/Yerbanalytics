package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoWeatherController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "yerbanalytics.demo.enabled=true")
@Import(DemoWeatherControllerTest.Reloj.class)
@DisplayName("DemoWeatherController")
class DemoWeatherControllerTest {

    /** 13:05 UTC = 10:05 en Buenos Aires. */
    @TestConfiguration
    static class Reloj {
        @Bean
        Clock relojVivero() {
            return Clock.fixed(Instant.parse("2026-10-05T13:05:00Z"), ZonaHorariaVivero.ZONA);
        }
    }

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 5);

    @Autowired
    MockMvc mvc;

    @MockBean
    WeatherService weatherService;

    private WeatherForecast inyectado() {
        ArgumentCaptor<WeatherForecast> captor = ArgumentCaptor.forClass(WeatherForecast.class);
        verify(weatherService).setMockForecast(captor.capture());
        return captor.getValue();
    }

    @BeforeEach
    void sinPronostico() {
        when(weatherService.getMockForecast()).thenReturn(null);
    }

    @Test
    @DisplayName("con los campos viejos genera 24 marcas y R-03 puede accionarse")
    void camposViejosGeneranMarcas() throws Exception {
        mvc.perform(post("/api/demo/weather").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"tempC\":28,\"humedadRelPct\":60,\"probLluviaPct\":80,\"precipMm\":8,\"uvIndex\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probLluviaPct").value(80.0));

        WeatherForecast f = inyectado();
        assertThat(f.horas()).hasSize(24);
        WeatherForecast.LluviaPrevista l = f.lluviaProxima(AHORA, 4);
        assertThat(l.probMaxPct()).isEqualTo(80.0);
        assertThat(l.mmTotal()).isGreaterThanOrEqualTo(5.0);
    }

    @Test
    @DisplayName("acepta uvMaxDia y marcas puestas a mano")
    void camposNuevos() throws Exception {
        mvc.perform(post("/api/demo/weather").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"tempC\":28,\"humedadRelPct\":60,\"probLluviaPct\":0,\"precipMm\":0,\"uvIndex\":5,"
                                + "\"uvMaxDia\":11,\"horas\":[{\"hora\":13,\"tempC\":36}]}"))
                .andExpect(status().isOk());

        WeatherForecast f = inyectado();
        assertThat(f.uvMaxDia()).isEqualTo(11.0);
        assertThat(f.horas().stream().filter(h -> h.hora().getHour() == 13).findFirst().orElseThrow().tempC())
                .isEqualTo(36.0);
    }

    @Test
    @DisplayName("una hora fuera de 0–23 es 400 y no inyecta nada")
    void horaInvalida() throws Exception {
        mvc.perform(post("/api/demo/weather").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"tempC\":28,\"humedadRelPct\":60,\"probLluviaPct\":0,\"precipMm\":0,\"uvIndex\":5,"
                                + "\"horas\":[{\"hora\":24,\"tempC\":36}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /offline activa y desactiva el modo sin pronóstico; GET informa el estado")
    void offline() throws Exception {
        mvc.perform(put("/api/demo/weather/offline").contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));
        verify(weatherService).setOfflineSimulated(true);

        when(weatherService.isOfflineSimulated()).thenReturn(true);
        mvc.perform(get("/api/demo/weather/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));
    }

    @Test
    @DisplayName("PUT /offline sin 'on' es 400")
    void offlineSinOn() throws Exception {
        mvc.perform(put("/api/demo/weather/offline").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE limpia el clima simulado")
    void deleteLimpia() throws Exception {
        mvc.perform(delete("/api/demo/weather")).andExpect(status().isNoContent());

        verify(weatherService).clearMockForecast();
    }
}
