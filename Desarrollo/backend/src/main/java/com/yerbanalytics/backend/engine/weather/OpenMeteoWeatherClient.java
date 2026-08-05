package com.yerbanalytics.backend.engine.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementación de {@link WeatherClient} usando la API pública de <b>Open-Meteo</b>
 * (open-meteo.com), gratuita y sin API key.
 *
 * <p>Esta clase es un <em>adapter</em>: traduce la respuesta JSON de Open-Meteo al
 * record {@link WeatherForecast} que consume el motor. Si se decide cambiar de proveedor,
 * basta con crear otra implementación de {@link WeatherClient} — ninguna regla cambia.
 *
 * <h3>Parámetros de configuración</h3>
 * <ul>
 *   <li>{@code yerbanalytics.weather.lat} — latitud del vivero (default: -25.29, Posadas)</li>
 *   <li>{@code yerbanalytics.weather.lon} — longitud del vivero (default: -57.64, Posadas)</li>
 * </ul>
 *
 * <h3>Campos usados de la API</h3>
 * <ul>
 *   <li>{@code hourly.precipitation_probability} — probabilidad de lluvia en %</li>
 *   <li>{@code hourly.uv_index} — índice UV</li>
 * </ul>
 */
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);

    private static final String BASE_URL = "https://api.open-meteo.com/v1";
    /** Offset de horas desde "ahora" que se toma como "próxima hora". */
    private static final int NEXT_HOUR_OFFSET = 1;

    private final RestClient restClient;
    private final double lat;
    private final double lon;

    public OpenMeteoWeatherClient(
            RestClient.Builder restClientBuilder,
            @Value("${yerbanalytics.weather.lat:-25.29}") double lat,
            @Value("${yerbanalytics.weather.lon:-57.64}") double lon) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.lat = lat;
        this.lon = lon;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Obtiene el pronóstico horario actual y retorna la próxima hora.
     * Retorna {@code null} ante cualquier error de red o parse.
     */
    @Override
    @SuppressWarnings("unchecked")
    public WeatherForecast fetch() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("hourly", "precipitation_probability,uv_index")
                            .queryParam("forecast_days", 1)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("WeatherClient: respuesta vacía de Open-Meteo.");
                return null;
            }

            Map<String, Object> hourly = (Map<String, Object>) response.get("hourly");
            if (hourly == null) {
                log.warn("WeatherClient: campo 'hourly' ausente en la respuesta.");
                return null;
            }

            List<Number> precipProb = (List<Number>) hourly.get("precipitation_probability");
            List<Number> uvIndex    = (List<Number>) hourly.get("uv_index");

            if (precipProb == null || uvIndex == null || precipProb.isEmpty() || uvIndex.isEmpty()) {
                log.warn("WeatherClient: campos de pronóstico ausentes o vacíos.");
                return null;
            }

            // Tomamos el valor de la "próxima hora" (índice 1, siendo índice 0 la hora actual)
            int idx = Math.min(NEXT_HOUR_OFFSET, precipProb.size() - 1);
            double prob = precipProb.get(idx).doubleValue();
            double uv   = uvIndex.get(idx).doubleValue();

            log.debug("WeatherClient: prob. lluvia próxima hora = {}%, UV = {}", prob, uv);
            return new WeatherForecast(prob, uv, Instant.now());

        } catch (RestClientException e) {
            log.warn("WeatherClient: error de red al consultar Open-Meteo: {}", e.getMessage());
            return null;
        } catch (ClassCastException | NullPointerException e) {
            log.warn("WeatherClient: error al parsear respuesta de Open-Meteo: {}", e.getMessage());
            return null;
        }
    }
}
