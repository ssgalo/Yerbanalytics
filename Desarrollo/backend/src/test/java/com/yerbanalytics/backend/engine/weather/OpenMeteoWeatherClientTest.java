package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/** Tarea 5.1: el cliente pide los milímetros y la zona horaria, y ubica la hora actual con el reloj del vivero. */
@DisplayName("OpenMeteoWeatherClient")
class OpenMeteoWeatherClientTest {

    /** 13:30 UTC son las 10:30 en Buenos Aires: la marca actual es la de las 10:00 locales. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-03T13:30:00Z"), ZonaHorariaVivero.ZONA);

    private MockRestServiceServer server;
    private OpenMeteoWeatherClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenMeteoWeatherClient(builder, -27.36, -55.90, RELOJ);
    }

    /** 48 marcas desde las 00:00 locales del 3/10: prob = índice, mm = índice / 10, temp = 20 + índice. */
    private static String respuesta(boolean conPrecipitacion) {
        return respuesta(conPrecipitacion, java.util.Set.of(), java.util.Set.of());
    }

    /** Como {@link #respuesta(boolean)} pero con {@code null} en las marcas dadas (índices) de probabilidad y de mm. */
    private static String respuesta(boolean conPrecipitacion, java.util.Set<Integer> probNull, java.util.Set<Integer> mmNull) {
        List<String> time = new ArrayList<>();
        List<String> prob = new ArrayList<>();
        List<String> mm = new ArrayList<>();
        List<String> otros = new ArrayList<>();
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 3, 0, 0);
        for (int i = 0; i < 48; i++) {
            time.add("\"" + t0.plusHours(i).toString() + "\"");
            prob.add(probNull.contains(i) ? "null" : String.valueOf(i));
            mm.add(mmNull.contains(i) ? "null" : String.format(Locale.US, "%.1f", i / 10.0));
            otros.add(String.valueOf(20 + i));
        }
        String lista = String.join(",", otros);
        return "{\"hourly\":{"
                + "\"time\":[" + String.join(",", time) + "],"
                + "\"precipitation_probability\":[" + String.join(",", prob) + "],"
                + (conPrecipitacion ? "\"precipitation\":[" + String.join(",", mm) + "]," : "")
                + "\"uv_index\":[" + lista + "],"
                + "\"temperature_2m\":[" + lista + "],"
                + "\"relative_humidity_2m\":[" + lista + "],"
                + "\"weathercode\":[" + "0,".repeat(47) + "0]}}";
    }

    @Test
    void pideLosMilimetrosYLaZonaHoraria() {
        server.expect(requestTo(containsString("/forecast")))
                .andExpect(method(GET))
                .andExpect(queryParam("timezone", "America/Argentina/Buenos_Aires"))
                .andExpect(queryParam("hourly", matchesPattern(".*(^|,)precipitation(,|$).*")))
                .andExpect(queryParam("hourly", containsString("precipitation_probability")))
                .andExpect(queryParam("forecast_days", "2"))
                .andRespond(withSuccess(respuesta(true), MediaType.APPLICATION_JSON));

        WeatherForecast f = client.fetch();

        assertThat(f).isNotNull();
        server.verify();
    }

    @Test
    void conUnClockFijoEnLas1330UtcTomaLaMarcaDeLas1000() {
        server.expect(requestTo(containsString("/forecast")))
                .andRespond(withSuccess(respuesta(true), MediaType.APPLICATION_JSON));

        WeatherForecast f = client.fetch();

        // La hora actual es la marca índice 10: probabilidad 10, temperatura 30.
        assertThat(f.probLluviaPct()).isEqualTo(10.0);
        assertThat(f.tempC()).isEqualTo(30.0);
        assertThat(f.horas().get(0).hora()).isEqualTo(LocalDateTime.of(2026, 10, 3, 10, 0));
        assertThat(f.timestamp()).isEqualTo(RELOJ.instant());
    }

    @Test
    void exponeLasHorasHastaVeinticuatroMasLaActual() {
        server.expect(requestTo(containsString("/forecast")))
                .andRespond(withSuccess(respuesta(true), MediaType.APPLICATION_JSON));

        WeatherForecast f = client.fetch();

        assertThat(f.horas()).hasSize(25);
        assertThat(f.horas().get(24).hora()).isEqualTo(LocalDateTime.of(2026, 10, 4, 10, 0));

        // Marcas 11 a 14: probabilidad máxima 14 y 1,1 + 1,2 + 1,3 + 1,4 = 5,0 mm.
        WeatherForecast.LluviaPrevista l = f.lluviaProxima(LocalDateTime.of(2026, 10, 3, 10, 20), 4);
        assertThat(l.probMaxPct()).isEqualTo(14.0);
        assertThat(l.mmTotal()).isEqualTo(5.0);
        assertThat(l.horasCubiertas()).isEqualTo(4);
    }

    @Test
    void sinLaListaDeMilimetrosDegradaSoloLaLluviaYConservaElRestoDelPronostico() {
        server.expect(requestTo(containsString("/forecast")))
                .andRespond(withSuccess(respuesta(false), MediaType.APPLICATION_JSON));

        WeatherForecast f = client.fetch();

        // El widget y la mediasombra (UV) siguen funcionando: la hora actual es la marca índice 10.
        assertThat(f).isNotNull();
        assertThat(f.uvIndex()).isEqualTo(30.0);
        assertThat(f.tempC()).isEqualTo(30.0);
        // Pero la lluvia acumulada no se conoce: no es "0 mm", es sin dato (R-03 queda SIN_DATO).
        WeatherForecast.LluviaPrevista l = f.lluviaProxima(LocalDateTime.of(2026, 10, 3, 10, 20), 4);
        assertThat(l.mmTotal()).isNull();
        assertThat(l.horasCubiertas()).isZero();
        assertThat(l.probMaxPct()).isEqualTo(14.0);
    }

    @Test
    void unaMarcaConMilimetrosNulosNoSeLeeComoCeroYNoCuentaComoCubierta() {
        // Marcas 11 a 14 de la ventana: la 12 no trae milímetros y la 13 no trae probabilidad.
        server.expect(requestTo(containsString("/forecast")))
                .andRespond(withSuccess(respuesta(true, java.util.Set.of(13), java.util.Set.of(12)), MediaType.APPLICATION_JSON));

        WeatherForecast f = client.fetch();

        assertThat(f.horas().get(2).precipitacionMm()).isNull();
        assertThat(f.horas().get(3).probLluviaPct()).isNull();
        WeatherForecast.LluviaPrevista l = f.lluviaProxima(LocalDateTime.of(2026, 10, 3, 10, 20), 4);
        assertThat(l.horasCubiertas()).isEqualTo(2);                       // sólo 11 y 14 traen los dos datos
        assertThat(l.mmTotal()).isEqualTo(3.8);                            // 1,1 + 1,3 + 1,4: la hora 12 no suma "0"
        assertThat(l.probMaxPct()).isEqualTo(14.0);
    }

    @Test
    void ante_unaFallaDeRedDevuelveNull() {
        server.expect(requestTo(containsString("/forecast"))).andRespond(withServerError());

        assertThat(client.fetch()).isNull();
    }

    // ------------------------------------------------------------------ timeouts HTTP

    @Test
    @Timeout(15)
    @DisplayName("si la API no responde, la lectura vence por timeout y fetch() devuelve null (no cuelga el hilo de refresco)")
    void unaApiQueNoRespondeVencePorTimeoutDeLectura() throws Exception {
        HttpServer colgado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        colgado.createContext("/", exchange -> {
            try {
                Thread.sleep(12_000);                    // acepta la conexión y no contesta
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        colgado.start();
        try {
            OpenMeteoWeatherClient lento = new OpenMeteoWeatherClient(RestClient.builder(),
                    "http://127.0.0.1:" + colgado.getAddress().getPort(), -27.36, -55.90, RELOJ, 500, 700);

            long desde = System.nanoTime();
            WeatherForecast resultado = lento.fetch();
            long ms = (System.nanoTime() - desde) / 1_000_000;

            assertThat(resultado).isNull();
            assertThat(ms).as("vence a los ~700 ms de lectura, no espera a la API").isLessThan(5_000);
        } finally {
            colgado.stop(0);
        }
    }

    @Test
    @DisplayName("la fábrica de pedidos lleva los timeouts de conexión y de lectura configurados")
    void laFabricaLlevaLosTimeouts() {
        SimpleClientHttpRequestFactory f = OpenMeteoWeatherClient.fabricaConTimeouts(3_000, 5_000);

        assertThat(ReflectionTestUtils.getField(f, "connectTimeout")).isEqualTo(3_000);
        assertThat(ReflectionTestUtils.getField(f, "readTimeout")).isEqualTo(5_000);
    }
}
