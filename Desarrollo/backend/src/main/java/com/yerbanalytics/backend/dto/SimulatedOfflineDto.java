package com.yerbanalytics.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body and response of {@code /api/demo/weather/offline}.
 *
 * @param on {@code true} = the forecast service answers "no forecast" (as if Open-Meteo were down)
 */
public record SimulatedOfflineDto(@NotNull Boolean on) {}
