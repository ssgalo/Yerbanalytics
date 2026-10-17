package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulatedForecastSpec")
class SimulatedForecastSpecTest {

    /** 13:05 UTC = 10:05 en Buenos Aires. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T13:05:00Z"), ZonaHorariaVivero.ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 5);

    private static SimulatedForecastSpec soloCamposViejos() {
        return new SimulatedForecastSpec(30.0, 55.0, 80.0, 8.0, 6.0, null, null);
    }

    @Test
    @DisplayName("con sólo los campos viejos genera 24 marcas planas desde la hora siguiente")
    void generaVeinticuatroMarcasPlanas() {
        WeatherForecast f = soloCamposViejos().toForecast(RELOJ);

        assertThat(f.horas()).hasSize(24);
        assertThat(f.horas().get(0).hora()).isEqualTo(LocalDateTime.of(2026, 10, 5, 11, 0));
        assertThat(f.horas().get(23).hora()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 0));
        assertThat(f.horas()).allSatisfy(h -> {
            assertThat(h.probLluviaPct()).isEqualTo(80.0);
            assertThat(h.precipitacionMm()).isEqualTo(8.0);
            assertThat(h.tempC()).isEqualTo(30.0);
        });
    }

    @Test
    @DisplayName("lluviaProxima(ahora, 4) devuelve 80 % y al menos 5 mm: R-03 se puede accionar")
    void lluviaProximaCumpleLosUmbralesDeR03() {
        WeatherForecast.LluviaPrevista l = soloCamposViejos().toForecast(RELOJ).lluviaProxima(AHORA, 4);

        assertThat(l.probMaxPct()).isEqualTo(80.0);
        assertThat(l.mmTotal()).isGreaterThanOrEqualTo(5.0);
        assertThat(l.horasCubiertas()).isEqualTo(4);
    }

    @Test
    @DisplayName("conserva los campos de la hora actual para el widget")
    void hoyDelWidget() {
        WeatherForecast f = soloCamposViejos().toForecast(RELOJ);

        assertThat(f.tempC()).isEqualTo(30.0);
        assertThat(f.humRel()).isEqualTo(55.0);
        assertThat(f.probLluviaPct()).isEqualTo(80.0);
        assertThat(f.uvIndex()).isEqualTo(6.0);
        assertThat(f.cond()).isEqualTo("Simulado");
        assertThat(f.timestamp()).isEqualTo(RELOJ.instant());
    }

    @Test
    @DisplayName("sin uvMaxDia, el máximo del día es el UV informado")
    void uvMaxDiaPorDefecto() {
        assertThat(soloCamposViejos().toForecast(RELOJ).uvMaxDia()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("uvMaxDia informado se respeta")
    void uvMaxDiaExplicito() {
        var spec = new SimulatedForecastSpec(30.0, 55.0, 0.0, 0.0, 2.0, 11.0, null);

        assertThat(spec.toForecast(RELOJ).uvMaxDia()).isEqualTo(11.0);
    }

    @Test
    @DisplayName("una marca puesta a mano pisa sólo los campos que trae; el resto queda plano")
    void marcaPisadaParcialmente() {
        var spec = new SimulatedForecastSpec(30.0, 55.0, 10.0, 0.0, 6.0, null,
                List.of(new SimulatedForecastSpec.Hour(13, null, null, 36.0)));

        WeatherForecast f = spec.toForecast(RELOJ);

        WeatherForecast.PronosticoHora trece = f.horas().stream()
                .filter(h -> h.hora().getHour() == 13).findFirst().orElseThrow();
        assertThat(trece.tempC()).isEqualTo(36.0);
        assertThat(trece.probLluviaPct()).isEqualTo(10.0);
        assertThat(f.horas().stream().filter(h -> h.hora().getHour() != 13))
                .allSatisfy(h -> assertThat(h.tempC()).isEqualTo(30.0));
    }

    @Test
    @DisplayName("una marca puesta a mano puede traer su propia lluvia")
    void marcaConLluviaPropia() {
        var spec = new SimulatedForecastSpec(25.0, 55.0, 0.0, 0.0, 6.0, null,
                List.of(new SimulatedForecastSpec.Hour(12, 90.0, 12.0, null)));

        WeatherForecast.LluviaPrevista l = spec.toForecast(RELOJ).lluviaProxima(AHORA, 4);

        assertThat(l.probMaxPct()).isEqualTo(90.0);
        assertThat(l.mmTotal()).isEqualTo(12.0);
    }
}
