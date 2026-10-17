package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.SimulatedOfflineDto;
import com.yerbanalytics.backend.dto.SimulatedWeatherInputDto;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * Demo-only REST API for simulated weather injection.
 *
 * <p>Only registered when {@code yerbanalytics.demo.enabled=true}.
 *
 * <p>While a mock forecast is active, every call to {@link WeatherService#getForecast()} and
 * {@link WeatherService#getForecastSinEspera()} returns it without hitting Open-Meteo. This
 * lets the operator trigger the rules that read the forecast on demand. The forecast it builds carries 24 hourly
 * marks (rain probability, mm and temperature) and the day's maximum UV, so rules that look at a window see it.
 * {@code /offline} simulates the absence of a forecast.
 */
@RestController
@RequestMapping("/api/demo/weather")
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoWeatherController {

    private final WeatherService weatherService;
    private final Clock reloj;

    public DemoWeatherController(WeatherService weatherService, Clock reloj) {
        this.weatherService = weatherService;
        this.reloj = reloj;
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
        WeatherForecast forecast = input.toSpec().toForecast(reloj);
        weatherService.setMockForecast(forecast);
        return ResponseEntity.ok(toMap(forecast));
    }

    /** Whether the "no forecast" mode is on. */
    @GetMapping("/offline")
    public SimulatedOfflineDto offline() {
        return new SimulatedOfflineDto(weatherService.isOfflineSimulated());
    }

    /** Turns the "no forecast" mode on or off: the service answers {@code null} as if Open-Meteo were down. */
    @PutMapping("/offline")
    public SimulatedOfflineDto setOffline(@Valid @RequestBody SimulatedOfflineDto body) {
        weatherService.setOfflineSimulated(body.on());
        return body;
    }

    /** Removes the simulated forecast and the "no forecast" mode; the service reverts to Open-Meteo. */
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
                "uvMaxDia", f.uvMaxDia(),
                "cond", f.cond(),
                "timestamp", f.timestamp().toEpochMilli());
    }
}
