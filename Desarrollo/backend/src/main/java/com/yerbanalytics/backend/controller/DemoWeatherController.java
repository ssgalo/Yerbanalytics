package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.SimulatedWeatherInputDto;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Demo-only REST API for simulated weather injection.
 *
 * <p>Only registered when {@code yerbanalytics.demo.enabled=true}.
 *
 * <p>While a mock forecast is active, every call to {@link WeatherService#getForecast()} and
 * {@link WeatherService#getForecastSinEspera()} returns it without hitting Open-Meteo. This
 * lets the operator trigger {@code PosponerPorLluviaRule} and {@code ShadingRule} on demand.
 */
@RestController
@RequestMapping("/api/demo/weather")
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoWeatherController {

    private final WeatherService weatherService;

    public DemoWeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** Returns the currently active simulated forecast, or 204 if none is set. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        WeatherForecast mock = weatherService.getMockForecast();
        if (mock == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toMap(mock));
    }

    /** Injects a simulated forecast; replaces any previous one. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> set(@Valid @RequestBody SimulatedWeatherInputDto input) {
        WeatherForecast forecast = new WeatherForecast(
                input.probLluviaPct(),
                input.uvIndex(),
                Instant.now(),
                input.tempC(),
                "Simulado",
                input.humedadRelPct(),
                List.of());
        weatherService.setMockForecast(forecast);
        return ResponseEntity.ok(toMap(forecast));
    }

    /** Removes the simulated forecast; the service reverts to Open-Meteo. */
    @DeleteMapping
    public ResponseEntity<Void> clear() {
        weatherService.clearMockForecast();
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> toMap(WeatherForecast f) {
        return Map.of(
                "tempC", f.tempC(),
                "humedadRelPct", f.humRel(),
                "probLluviaPct", f.probLluviaPct(),
                "uvIndex", f.uvIndex(),
                "cond", f.cond(),
                "timestamp", f.timestamp().toEpochMilli());
    }
}
