package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.WeatherOverrideRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link WeatherOverrideRule} (tarea 3.4).
 *
 * <p>Cubre los cuatro escenarios del design: sin forecast, forecast sin lluvia,
 * forecast con lluvia bajo umbral, y forecast con lluvia sobre umbral.
 */
@DisplayName("WeatherOverrideRule")
class WeatherOverrideRuleTest {

    private static final double UMBRAL = 60.0;

    private WeatherOverrideRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new WeatherOverrideRule();
        sector = RuleContextTestFactory.sectorBasico("MZ-2-010");
        zona = RuleContextTestFactory.zonaBasica("MZ-2");
    }

    @Test
    @DisplayName("sin forecast (API caída) → NOOP_INFO de degradación")
    void sinForecast_emiteNoopInfo() {
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, null);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("degradada");
    }

    @Test
    @DisplayName("lluvia < umbral → NOOP_INFO, no se pospone")
    void conLluviaBajoUmbral_emiteNoopInfo() {
        WeatherForecast forecast = new WeatherForecast(30.0, 3.0, Instant.now(), 20.0, "Nublado", 50.0, java.util.List.of());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("30");
    }

    @Test
    @DisplayName("lluvia == umbral → POSTPONE_RIEGO")
    void conLluviaExactoUmbral_emitePostpone() {
        WeatherForecast forecast = new WeatherForecast(UMBRAL, 2.0, Instant.now(), 20.0, "Lluvia", 80.0, java.util.List.of());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.POSTPONE_RIEGO);
    }

    @Test
    @DisplayName("lluvia > umbral → POSTPONE_RIEGO con motivo descriptivo")
    void conLluviaSupeorUmbral_emitePostponeConMotivo() {
        WeatherForecast forecast = new WeatherForecast(85.0, 1.0, Instant.now(), 20.0, "Tormenta", 90.0, java.util.List.of());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        RuleAction accion = acciones.get(0);
        assertThat(accion.type()).isEqualTo(ActionType.POSTPONE_RIEGO);
        assertThat(accion.ruleName()).isEqualTo("WeatherOverrideRule");
        assertThat(accion.motivo()).contains("85").contains("60");
    }

    @Test
    @DisplayName("declara la probabilidad de lluvia y registra la comparación")
    void declaraElParametroYRegistraLaComparacion() {
        WeatherForecast forecast = new WeatherForecast(85.0, 1.0, Instant.now(), 20.0, "Tormenta", 90.0, java.util.List.of());
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.conForecast(sector, zona, forecast), ev);

        assertThat(rule.parametros()).containsExactly(ParametrosRiego.LLUVIA_PROBABILIDAD);
        assertThat(ev.comparaciones()).hasSize(1);
        assertThat(ev.comparaciones().get(0).recibido()).isEqualTo(85.0);
        assertThat(ev.comparaciones().get(0).operador()).isEqualTo(Operador.GE);
        assertThat(ev.comparaciones().get(0).umbral()).isEqualTo(UMBRAL);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("sin pronóstico → SIN_DATO y la cadena sigue")
    void sinPronosticoLaTrazaQuedaSinDato() {
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.conForecast(sector, zona, null), ev);

        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("el umbral sale del catálogo")
    void elUmbralSaleDelCatalogo() {
        WeatherForecast forecast = new WeatherForecast(65.0, 1.0, Instant.now(), 20.0, "Nublado", 90.0, java.util.List.of());

        List<RuleAction> acciones = rule.evaluate(RuleContextTestFactory.conForecast(sector, zona, forecast),
                ev(rule, java.util.Map.of(ParametrosRiego.LLUVIA_PROBABILIDAD, "70")));

        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("POSTPONE_RIEGO es una acción bloqueante")
    void postponeRiegoEsBloqueante() {
        assertThat(ActionType.POSTPONE_RIEGO.isBlocking()).isTrue();
    }
}
