package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.engine.weather.WeatherForecast;

import java.time.Instant;

/**
 * Todo lo que las precondiciones de una aplicación necesitan saber, ya consultado: así la evaluación es una función
 * pura y su test no necesita repositorios.
 *
 * @param supply         qué se quiere aplicar
 * @param now            instante actual del vivero
 * @param diagnosis      veredicto de P-D1, o {@code null} si la aplicación no la dispara un diagnóstico
 * @param lastSameSupply última aplicación de ESTE insumo en el sector, o {@code null}
 * @param lastAnySupply  última aplicación de cualquier insumo en el sector, o {@code null}
 * @param airTemp        temperatura (última lectura de la zona o, si falta, la del pronóstico), o {@code null}
 * @param soilMoisture   última humedad de sustrato de la zona, o {@code null}
 * @param moistureAt     cuándo se leyó {@code soilMoisture}, o {@code null}
 * @param lastWatering   último riego del sector, o {@code null}
 * @param rain           lluvia prevista en la ventana de P-D4, o {@code null} si no hay pronóstico
 * @param fungicidesInCapWindow fitosanitarios que recibió el sector en la ventana del tope (F-06), desde la última revisión; 0 si el insumo no es fitosanitario
 */
public record ApplicationFacts(
        Supply supply,
        Instant now,
        ValidDiagnosis.Verdict diagnosis,
        Instant lastSameSupply,
        Instant lastAnySupply,
        Double airTemp,
        Double soilMoisture,
        Instant moistureAt,
        Instant lastWatering,
        WeatherForecast.LluviaPrevista rain,
        int fungicidesInCapWindow) {

    /** Sin tope: lo que se ve antes de que existiera F-06 (ningún fitosanitario en la ventana). */
    public ApplicationFacts(Supply supply, Instant now, ValidDiagnosis.Verdict diagnosis, Instant lastSameSupply,
                            Instant lastAnySupply, Double airTemp, Double soilMoisture, Instant moistureAt,
                            Instant lastWatering, WeatherForecast.LluviaPrevista rain) {
        this(supply, now, diagnosis, lastSameSupply, lastAnySupply, airTemp, soilMoisture, moistureAt, lastWatering,
                rain, 0);
    }
}
