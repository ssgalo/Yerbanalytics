package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Armador de {@link RuleContext} para los tests de las reglas de riego: hora LOCAL del vivero
 * (el 2026-10-05, un lunes), humedad de sustrato, pronóstico, bloqueo y pasado reciente del sector.
 *
 * <p>La zona queda con la lectura y la humedad frescas (para que {@code StaleSensorRule}, si participa,
 * no corte) y el ciclo de lectura arranca solo del reloj (240 min anclados a las 02:00).
 */
public final class RiegoCtx {

    public static final LocalDate DIA = LocalDate.of(2026, 10, 5);

    private Instant ahora;
    private Double humedad;
    private WeatherForecast forecast;
    private boolean bloqueo;
    private Instant inicioCiclo;
    private Long ultimoRiegoMs;
    private Long ultimoRiegoCriticoMs;
    private Long ultimaAplicacionMs;
    private Long riegoEnCursoHastaMs;
    private boolean contextoVacio = true;

    private RiegoCtx(Instant ahora) {
        this.ahora = ahora;
    }

    /** Hora local {@code "HH:mm"} o {@code "HH:mm:ss"} del {@link #DIA}. */
    public static RiegoCtx a(String horaLocal) {
        return new RiegoCtx(instante(horaLocal));
    }

    /** El instante UTC de una hora local del {@link #DIA}. */
    public static Instant instante(String horaLocal) {
        LocalTime t = LocalTime.parse(horaLocal.length() == 5 ? horaLocal + ":00" : horaLocal);
        return ZonedDateTime.of(DIA, t, ZonaHorariaVivero.ZONA).toInstant();
    }

    /** Hora local de hoy ({@link #DIA}) menos {@code horas} h {@code min} min {@code seg} s, en epoch ms. */
    public static long haceMs(Instant ahora, int horas, int min, int seg) {
        return ahora.toEpochMilli() - ((horas * 3600L + min * 60L + seg) * 1000L);
    }

    public RiegoCtx humedad(Double h) {
        this.humedad = h;
        return this;
    }

    public RiegoCtx forecast(WeatherForecast f) {
        this.forecast = f;
        return this;
    }

    public RiegoCtx bloqueado() {
        this.bloqueo = true;
        return this;
    }

    public RiegoCtx inicioCiclo(String horaLocal) {
        this.inicioCiclo = instante(horaLocal);
        this.contextoVacio = false;
        return this;
    }

    public RiegoCtx ultimoRiego(String horaLocal) {
        this.ultimoRiegoMs = instante(horaLocal).toEpochMilli();
        this.contextoVacio = false;
        return this;
    }

    public RiegoCtx ultimoRiegoCriticoHace(int horas, int min) {
        this.ultimoRiegoCriticoMs = haceMs(ahora, horas, min, 0);
        this.contextoVacio = false;
        return this;
    }

    public RiegoCtx ultimaAplicacionHace(int horas, int min, int seg) {
        this.ultimaAplicacionMs = haceMs(ahora, horas, min, seg);
        this.contextoVacio = false;
        return this;
    }

    public RiegoCtx riegoEnCursoHasta(String horaLocal) {
        this.riegoEnCursoHastaMs = instante(horaLocal).toEpochMilli();
        this.contextoVacio = false;
        return this;
    }

    public Instant ahora() {
        return ahora;
    }

    public RuleContext build() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-2-001");
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-2");
        zona.setLastReadingTime(ahora.toEpochMilli() - 10_000);
        zona.setHumSusTs(ahora.toEpochMilli() - 10_000);
        sector.setZona(zona);
        List<Metric> metricas = humedad == null ? List.of()
                : List.of(new Metric("humSus", "Humedad de sustrato", "%", humedad, humedad + "", "ok", "#000", null));
        ContextoRiego riego = contextoVacio ? ContextoRiego.vacio()
                : new ContextoRiego(inicioCiclo, ultimoRiegoMs, ultimoRiegoCriticoMs, ultimaAplicacionMs,
                        riegoEnCursoHastaMs, zona.getHumSusTs());
        return new RuleContext(sector, zona, List.of(), metricas, RuleContextTestFactory.defaultConfig(),
                "ok", ahora, forecast, bloqueo, riego);
    }

    /** Pronóstico con una marca por hora (a partir de la hora siguiente) y los valores dados. */
    public static WeatherForecast pronostico(Instant ahora, double[] probPct, double[] mm) {
        LocalDateTime desde = ahora.atZone(ZonaHorariaVivero.ZONA).toLocalDateTime()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        java.util.ArrayList<WeatherForecast.PronosticoHora> horas = new java.util.ArrayList<>();
        for (int i = 0; i < probPct.length; i++) {
            horas.add(new WeatherForecast.PronosticoHora(desde.plusHours(i + 1L), probPct[i], mm[i]));
        }
        return new WeatherForecast(probPct.length == 0 ? 0 : probPct[0], 5.0, ahora, 22.0, "Parcial nublado",
                70.0, List.of(), horas);
    }
}
