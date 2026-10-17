package com.yerbanalytics.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Una marca horaria que {@code POST /api/demo/weather} pisa sobre el clima plano. Los campos ausentes dejan el
 * valor plano de esa hora.
 *
 * @param hora          hora del día (0–23, hora local del vivero) de la marca; es el fin de la hora que describe
 * @param probLluviaPct probabilidad de lluvia en % (0–100)
 * @param precipMm      milímetros previstos (≥ 0)
 * @param tempC         temperatura del aire en °C
 */
public record SimulatedHourDto(
        @Min(0) @Max(23) int hora,
        @DecimalMin("0") @DecimalMax("100") Double probLluviaPct,
        @DecimalMin("0") Double precipMm,
        Double tempC
) {}
