package com.yerbanalytics.backend.engine.weather;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Qué clima quiere inyectar el operador del simulador, y cómo se convierte en un {@link WeatherForecast}.
 *
 * <p>Los valores base describen la hora actual y se repiten, planos, en las 24 marcas horarias siguientes (de modo que
 * R-03, P-D3/P-D4 y M-02 los ven en cualquier ventana). {@code hours} permite pisar una marca puntual: sólo cambian los
 * campos que traiga, el resto queda con el valor plano.
 *
 * @param tempC         temperatura del aire en °C
 * @param humRelPct     humedad relativa en %
 * @param probLluviaPct probabilidad de lluvia en %
 * @param precipMm      milímetros previstos por cada hora
 * @param uvIndex       índice UV de la hora actual
 * @param uvMaxDia      UV máximo del día; si es {@code null} se usa {@code uvIndex}
 * @param hours         marcas a pisar; {@code null} o vacía = todas planas
 */
public record SimulatedForecastSpec(double tempC, double humRelPct, double probLluviaPct, double precipMm,
                                    double uvIndex, Double uvMaxDia, List<Hour> hours) {

    /** Marcas horarias que se generan hacia adelante. */
    static final int HORAS = 24;

    public SimulatedForecastSpec {
        hours = hours == null ? List.of() : List.copyOf(hours);
    }

    /**
     * Una marca puntual a pisar.
     *
     * @param hora          hora del día (0–23, hora local del vivero) de la marca, que es el FIN de la hora que
     *                      describe: 13 = la lectura de las 12:00 a las 13:00
     * @param probLluviaPct probabilidad de lluvia en %, o {@code null} para dejar la plana
     * @param precipMm      milímetros, o {@code null} para dejar el plano
     * @param tempC         temperatura en °C, o {@code null} para dejar la plana
     */
    public record Hour(int hora, Double probLluviaPct, Double precipMm, Double tempC) {
    }

    /** El pronóstico con las 24 marcas siguientes a la hora actual del {@code reloj} (el del vivero). */
    public WeatherForecast toForecast(Clock reloj) {
        LocalDateTime desde = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS);
        List<WeatherForecast.PronosticoHora> marcas = new ArrayList<>(HORAS);
        for (int i = 1; i <= HORAS; i++) {
            LocalDateTime marca = desde.plusHours(i);
            marcas.add(marcaDe(marca));
        }
        return new WeatherForecast(probLluviaPct, uvIndex, reloj.instant(), tempC, "Simulado", humRelPct,
                List.of(), marcas, uvMaxDia != null ? uvMaxDia : uvIndex);
    }

    private WeatherForecast.PronosticoHora marcaDe(LocalDateTime marca) {
        Hour pisada = hours.stream().filter(h -> h.hora() == marca.getHour()).findFirst().orElse(null);
        if (pisada == null) {
            return new WeatherForecast.PronosticoHora(marca, probLluviaPct, precipMm, tempC);
        }
        return new WeatherForecast.PronosticoHora(marca,
                pisada.probLluviaPct() != null ? pisada.probLluviaPct() : probLluviaPct,
                pisada.precipMm() != null ? pisada.precipMm() : precipMm,
                pisada.tempC() != null ? pisada.tempC() : tempC);
    }
}
