package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.weather.WeatherForecast;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Las condiciones de riego que NO son la humedad, en un solo lugar: las usan las reglas (que DECIDEN y dejan la
 * comparación en la traza) y el {@link DespachoRiego} (que las REVALIDA al abrir cada válvula, porque una solicitud
 * encolada es una decisión vieja: la ronda de 100 sectores tarda ~80 min en despacharse). Reglas y despacho leen los
 * mismos parámetros del catálogo; acá sólo está la condición, nunca el umbral.
 */
public final class PrecondicionesRiego {

    private static final double MS_POR_HORA = 3_600_000.0;

    private PrecondicionesRiego() {
    }

    /** Horas (con decimales) entre un instante en epoch ms y {@code ahora}. */
    public static double horasDesde(long epochMs, Instant ahora) {
        return (ahora.toEpochMilli() - epochMs) / MS_POR_HORA;
    }

    /**
     * R-06: {@code true} si el sector recibió un insumo hace menos de {@code pausaHoras}
     * ({@code riego.pausa-tras-aplicacion}). Sin aplicaciones recientes no hay pausa.
     */
    public static boolean enPausaPorAplicacion(Long ultimaAplicacionMs, Instant ahora, double pausaHoras) {
        return ultimaAplicacionMs != null && horasDesde(ultimaAplicacionMs, ahora) < pausaHoras;
    }

    /**
     * R-03: lluvia prevista en las próximas {@code horas} desde {@code ahoraLocal}, o {@code null} si no hay
     * pronóstico (O-01: sin dato no se pospone).
     */
    public static WeatherForecast.LluviaPrevista lluviaPrevista(WeatherForecast pronostico, LocalDateTime ahoraLocal, int horas) {
        return pronostico == null ? null : pronostico.lluviaProxima(ahoraLocal, horas);
    }

    /**
     * R-03: {@code true} si la lluvia prevista alcanza los DOS umbrales ({@code riego.lluvia-probabilidad} y
     * {@code riego.lluvia-mm}). Una hora sin dato no es una hora seca: si falta la probabilidad o los milímetros no
     * se pospone.
     */
    public static boolean lluviaPospone(WeatherForecast.LluviaPrevista lluvia, double probabilidadMinPct, double mmMin) {
        return lluvia != null && lluvia.probMaxPct() != null && lluvia.mmTotal() != null
                && lluvia.probMaxPct() >= probabilidadMinPct && lluvia.mmTotal() >= mmMin;
    }

    /**
     * Guarda de ciclo (R-01): {@code true} si el sector ya tuvo un riego despachado desde el inicio del ciclo de
     * lectura en curso. Es la misma condición que {@code CicloLecturaRiegoRule} muestra en minutos.
     */
    public static boolean yaRegoEnElCiclo(Long ultimoRiegoMs, Instant inicioCiclo) {
        return ultimoRiegoMs != null && inicioCiclo != null && ultimoRiegoMs >= inicioCiclo.toEpochMilli();
    }

    /**
     * Tope de R-02: {@code true} si el sector ya recibió un riego por déficit crítico hace menos de
     * {@code topeHoras} ({@code riego.exceptuado-bloqueo}).
     */
    public static boolean dentroDelTopeCritico(Long ultimoRiegoCriticoMs, Instant ahora, double topeHoras) {
        return ultimoRiegoCriticoMs != null && horasDesde(ultimoRiegoCriticoMs, ahora) < topeHoras;
    }
}
