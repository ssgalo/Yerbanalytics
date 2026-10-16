package com.yerbanalytics.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/demo/weather}.
 *
 * <p>All fields map to the values of {@link com.yerbanalytics.backend.engine.weather.WeatherForecast}.
 *
 * @param tempC          air temperature in °C
 * @param humedadRelPct  relative humidity in % (0–100)
 * @param probLluviaPct  rain probability in % (0–100)
 * @param precipMm       expected precipitation in mm (≥ 0)
 * @param uvIndex        UV index (≥ 0)
 */
public record SimulatedWeatherInputDto(
        @NotNull Double tempC,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double humedadRelPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double probLluviaPct,
        @NotNull @DecimalMin("0") Double precipMm,
        @NotNull @DecimalMin("0") Double uvIndex
) {}
