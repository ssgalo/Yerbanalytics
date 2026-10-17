package com.yerbanalytics.backend.dto;

import com.yerbanalytics.backend.engine.weather.SimulatedForecastSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body of {@code POST /api/demo/weather}.
 *
 * <p>The first five fields describe the current hour and are repeated, flat, in the next 24 hourly marks, so that
 * rules looking at a rain window see them. {@code uvMaxDia} and {@code horas} are optional and only refine that.
 *
 * @param tempC          air temperature in °C
 * @param humedadRelPct  relative humidity in % (0–100)
 * @param probLluviaPct  rain probability in % (0–100)
 * @param precipMm       expected precipitation in mm per hour (≥ 0)
 * @param uvIndex        UV index (≥ 0)
 * @param uvMaxDia       maximum UV of the day; defaults to {@code uvIndex}
 * @param horas          hourly marks to override; absent = all flat
 */
public record SimulatedWeatherInputDto(
        @NotNull Double tempC,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double humedadRelPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double probLluviaPct,
        @NotNull @DecimalMin("0") Double precipMm,
        @NotNull @DecimalMin("0") Double uvIndex,
        @DecimalMin("0") Double uvMaxDia,
        @Valid List<SimulatedHourDto> horas
) {

    /** What the operator asked for, in the terms of the weather package. */
    public SimulatedForecastSpec toSpec() {
        List<SimulatedForecastSpec.Hour> marcas = horas == null ? List.of() : horas.stream()
                .map(h -> new SimulatedForecastSpec.Hour(h.hora(), h.probLluviaPct(), h.precipMm(), h.tempC()))
                .toList();
        return new SimulatedForecastSpec(tempC, humedadRelPct, probLluviaPct, precipMm, uvIndex, uvMaxDia, marcas);
    }
}
