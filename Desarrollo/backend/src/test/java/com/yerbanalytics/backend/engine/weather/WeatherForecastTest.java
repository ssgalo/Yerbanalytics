package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.dto.Weather;
import com.yerbanalytics.backend.engine.weather.WeatherForecast.PronosticoHora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tareas 5.2 y 5.3: lluvia acumulada en una ventana de N horas y compatibilidad del constructor viejo. */
@DisplayName("WeatherForecast")
class WeatherForecastTest {

    private static final LocalDateTime DIEZ = LocalDateTime.of(2026, 10, 3, 10, 0);

    private static PronosticoHora marca(int hora, double prob, double mm) {
        return new PronosticoHora(LocalDateTime.of(2026, 10, 3, 0, 0).plusHours(hora), prob, mm);
    }

    private static WeatherForecast con(List<PronosticoHora> horas) {
        return new WeatherForecast(0, 0, Instant.now(), 20, "Despejado", 50, List.of(), horas);
    }

    /** Marcas 10:00 a 15:00 de la spec: la de las 15 cae afuera de una ventana de 4 h desde las 10:20. */
    private static List<PronosticoHora> escenarioSpec() {
        return List.of(
                marca(10, 99, 50),   // la hora actual (10:00): ya pasó, no cuenta
                marca(11, 40, 0),
                marca(12, 70, 2),
                marca(13, 55, 3),
                marca(14, 20, 0),
                marca(15, 90, 10));  // fuera de la ventana de 4 h
    }

    @Test
    void ventanaDeCuatroHorasDesdeLasDiezYVeinte() {
        WeatherForecast.LluviaPrevista l = con(escenarioSpec()).lluviaProxima(DIEZ.plusMinutes(20), 4);

        assertThat(l.probMaxPct()).isEqualTo(70.0);
        assertThat(l.mmTotal()).isEqualTo(5.0);
        assertThat(l.horasCubiertas()).isEqualTo(4);
    }

    @Test
    void excluyeLaMarcaDeLaHoraActualYLaDeLaHoraSiguienteALaVentana() {
        WeatherForecast f = con(escenarioSpec());

        // Con ventana de 1 h sólo cuenta la marca de las 11:00 (la que cubre 10-11).
        WeatherForecast.LluviaPrevista una = f.lluviaProxima(DIEZ.plusMinutes(20), 1);
        assertThat(una.probMaxPct()).isEqualTo(40.0);
        assertThat(una.mmTotal()).isEqualTo(0.0);
        assertThat(una.horasCubiertas()).isEqualTo(1);

        // Justo a las 10:00:00 la ventana es la misma: la marca de las 10 no entra nunca.
        assertThat(f.lluviaProxima(DIEZ, 4)).isEqualTo(f.lluviaProxima(DIEZ.plusMinutes(59), 4));
    }

    @Test
    void conMenosHorasDisponiblesDevuelveHorasCubiertasMenorQueLaVentana() {
        WeatherForecast f = con(List.of(marca(10, 0, 0), marca(11, 30, 1), marca(12, 80, 2)));

        WeatherForecast.LluviaPrevista l = f.lluviaProxima(DIEZ.plusMinutes(20), 4);

        assertThat(l.horasCubiertas()).isEqualTo(2);
        assertThat(l.probMaxPct()).isEqualTo(80.0);
        assertThat(l.mmTotal()).isEqualTo(3.0);
    }

    @Test
    void sinMarcasEnLaVentanaNoHayCobertura() {
        assertThat(con(List.of()).lluviaProxima(DIEZ, 4).horasCubiertas()).isZero();
        assertThat(con(List.of(marca(10, 50, 5))).lluviaProxima(DIEZ, 4).horasCubiertas()).isZero();
    }

    @Test
    void lluviaFueraDeLaVentanaNoCuenta() {
        List<PronosticoHora> horas = new ArrayList<>();
        for (int h = 10; h <= 16; h++) {
            horas.add(marca(h, h >= 15 ? 95 : 10, h >= 15 ? 10 : 0));  // lluvia sólo desde la marca +5
        }

        WeatherForecast.LluviaPrevista l = con(horas).lluviaProxima(DIEZ.plusMinutes(20), 4);

        assertThat(l.probMaxPct()).isEqualTo(10.0);
        assertThat(l.mmTotal()).isEqualTo(0.0);
    }

    @Test
    void unaHoraSinDatoNoCuentaNiSeLeeComoCero() {
        WeatherForecast f = con(List.of(
                new PronosticoHora(LocalDateTime.of(2026, 10, 3, 11, 0), null, 2.0),
                new PronosticoHora(LocalDateTime.of(2026, 10, 3, 12, 0), 60.0, null),
                new PronosticoHora(LocalDateTime.of(2026, 10, 3, 13, 0), 40.0, 1.0)));

        WeatherForecast.LluviaPrevista l = f.lluviaProxima(DIEZ, 4);

        assertThat(l.horasCubiertas()).isEqualTo(1);
        assertThat(l.probMaxPct()).isEqualTo(60.0);
        assertThat(l.mmTotal()).isEqualTo(3.0);
    }

    @Test
    void sinNingunDatoDeMilimetrosEnLaVentanaElTotalEsNull() {
        WeatherForecast f = con(List.of(
                new PronosticoHora(LocalDateTime.of(2026, 10, 3, 11, 0), 60.0, null)));

        assertThat(f.lluviaProxima(DIEZ, 4).mmTotal()).isNull();
        assertThat(f.lluviaProxima(DIEZ, 4).horasCubiertas()).isZero();
    }

    @Test
    void laFrescuraSeMideContraElRelojInyectado() {
        Instant t = Instant.parse("2026-10-03T13:00:00Z");
        WeatherForecast f = new WeatherForecast(10.0, 1.0, t, 20.0, "x", 40.0, List.of());

        assertThat(f.isFresh(900_000, t.plusSeconds(899))).isTrue();
        assertThat(f.isFresh(900_000, t.plusSeconds(900))).isFalse();
    }

    @Test
    void elConstructorViejoDejaLasHorasVacias() {
        WeatherForecast viejo = new WeatherForecast(10.0, 9.5, Instant.now(), 20.0, "Soleado", 40.0, List.of());

        assertThat(viejo.horas()).isEmpty();
        assertThat(viejo.probLluviaPct()).isEqualTo(10.0);
        assertThat(viejo.lluviaProxima(DIEZ, 4).horasCubiertas()).isZero();
    }

    @Test
    void elWidgetDelDashboardNoCambiaConLasHoras() throws Exception {
        Instant t = Instant.now();
        List<WeatherForecast.ForecastSlotRaw> slots = List.of(new WeatherForecast.ForecastSlotRaw("11 h", 3.0, 40.0));
        WeatherForecast viejo = new WeatherForecast(40.0, 5.0, t, 22.0, "Nublado", 60.0, slots);
        WeatherForecast nuevo = new WeatherForecast(40.0, 5.0, t, 22.0, "Nublado", 60.0, slots, escenarioSpec());

        Method m = com.yerbanalytics.backend.service.NurseryService.class
                .getDeclaredMethod("buildWeatherFromForecast", WeatherForecast.class);
        m.setAccessible(true);

        assertThat((Weather) m.invoke(null, nuevo)).isEqualTo((Weather) m.invoke(null, viejo));
    }
}
