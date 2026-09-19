package com.yerbanalytics.backend.engine.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 *   <li>{@code hourly.temperature_2m} — temperatura del aire en °C</li>
 *   <li>{@code hourly.relative_humidity_2m} — humedad relativa del aire en %</li>
 *   <li>{@code hourly.weathercode} — código WMO de condición climática</li>
 * </ul>
 */
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);

    private static final String BASE_URL = "https://api.open-meteo.com/v1";
    /** Número de slots horarios futuros a incluir en el pronóstico del widget. */
    private static final int FORECAST_SLOTS = 4;

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
     * <p>Obtiene el pronóstico horario actual y retorna la hora presente + próximos slots.
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
                            .queryParam("hourly", "precipitation_probability,uv_index,temperature_2m,relative_humidity_2m,weathercode")
                            .queryParam("forecast_days", 2)
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
            List<Number> uvList     = (List<Number>) hourly.get("uv_index");
            List<Number> tempList   = (List<Number>) hourly.get("temperature_2m");
            List<Number> humList    = (List<Number>) hourly.get("relative_humidity_2m");
            List<Number> wmoList    = (List<Number>) hourly.get("weathercode");
            List<String> timeList   = (List<String>) hourly.get("time");

            if (precipProb == null || uvList == null || tempList == null
                    || humList == null || wmoList == null || precipProb.isEmpty()) {
                log.warn("WeatherClient: campos de pronóstico ausentes o vacíos.");
                return null;
            }

            // Índice de la hora actual en el array de Open-Meteo
            int currentIdx = currentHourIndex(timeList);

            double prob   = safeDouble(precipProb, currentIdx);
            double uv     = safeDouble(uvList, currentIdx);
            double tempC  = safeDouble(tempList, currentIdx);
            double humRel = safeDouble(humList, currentIdx);
            int    wmo    = (int) safeDouble(wmoList, currentIdx);
            String cond   = wmoToCondicion(wmo);

            // Construir los próximos FORECAST_SLOTS slots horarios para el widget
            List<WeatherForecast.ForecastSlotRaw> slots = new ArrayList<>();
            for (int i = 1; i <= FORECAST_SLOTS; i++) {
                int idx = currentIdx + i;
                if (idx >= precipProb.size()) break;
                String label = buildSlotLabel(i, timeList, idx);
                double slotUv   = safeDouble(uvList, idx);
                double slotRain = safeDouble(precipProb, idx);
                slots.add(new WeatherForecast.ForecastSlotRaw(label, slotUv, slotRain));
            }

            log.debug("WeatherClient: temp={}°C cond={} lluvia={}% UV={}", tempC, cond, prob, uv);
            return new WeatherForecast(prob, uv, Instant.now(), tempC, cond, humRel, slots);

        } catch (RestClientException e) {
            log.warn("WeatherClient: error de red al consultar Open-Meteo: {}", e.getMessage());
            return null;
        } catch (ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
            log.warn("WeatherClient: error al parsear respuesta de Open-Meteo: {}", e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Índice de la hora actual dentro del array horario de Open-Meteo. */
    private int currentHourIndex(List<String> times) {
        if (times == null || times.isEmpty()) return 0;
        int hour = LocalTime.now(ZoneId.systemDefault()).getHour();
        // Open-Meteo devuelve timestamps en formato "2026-09-19T15:00"; buscamos la hora actual.
        for (int i = 0; i < times.size(); i++) {
            String t = times.get(i);
            if (t != null && t.endsWith("T" + String.format("%02d:00", hour))) {
                return i;
            }
        }
        return 0;
    }

    /** Etiqueta legible para un slot futuro: "16 h", "17 h", … o "Mañana" si cruza medianoche. */
    private String buildSlotLabel(int offset, List<String> times, int idx) {
        if (times != null && idx < times.size()) {
            String t = times.get(idx);
            if (t != null && t.length() >= 13) {
                String hPart = t.substring(11, 13); // "HH"
                int slotHour = Integer.parseInt(hPart);
                int todayHour = LocalTime.now(ZoneId.systemDefault()).getHour();
                if (slotHour < todayHour && offset > 0) {
                    return "Mañana";
                }
                return slotHour + " h";
            }
        }
        return "+" + offset + " h";
    }

    private double safeDouble(List<Number> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size() || list.get(idx) == null) return 0.0;
        return list.get(idx).doubleValue();
    }

    /**
     * Convierte un código WMO (World Meteorological Organization) a una condición legible.
     * Referencia: https://open-meteo.com/en/docs#weathervariables
     */
    private String wmoToCondicion(int code) {
        return switch (code) {
            case 0             -> "Despejado";
            case 1             -> "Mayormente despejado";
            case 2             -> "Parcial nublado";
            case 3             -> "Nublado";
            case 45, 48        -> "Niebla";
            case 51, 53, 55    -> "Llovizna";
            case 56, 57        -> "Llovizna helada";
            case 61, 63, 65    -> "Lluvia";
            case 66, 67        -> "Lluvia helada";
            case 71, 73, 75    -> "Nieve";
            case 77            -> "Granizo";
            case 80, 81, 82    -> "Chubascos";
            case 85, 86        -> "Nevadas";
            case 95            -> "Tormenta";
            case 96, 99        -> "Tormenta con granizo";
            default            -> "Variable";
        };
    }
}
