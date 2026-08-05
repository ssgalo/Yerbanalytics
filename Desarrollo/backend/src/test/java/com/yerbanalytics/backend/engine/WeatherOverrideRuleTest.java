package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.WeatherOverrideRule;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
        rule = new WeatherOverrideRule(UMBRAL);
        sector = RuleContextTestFactory.sectorBasico("MZ-2-010");
        zona = RuleContextTestFactory.zonaBasica("MZ-2");
    }

    @Test
    @DisplayName("sin forecast (API caída) → NOOP_INFO de degradación")
    void sinForecast_emiteNoopInfo() {
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, null);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("degradada");
    }

    @Test
    @DisplayName("lluvia < umbral → NOOP_INFO, no se pospone")
    void conLluviaBajoUmbral_emiteNoopInfo() {
        WeatherForecast forecast = new WeatherForecast(30.0, 3.0, Instant.now());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("30");
    }

    @Test
    @DisplayName("lluvia == umbral → POSTPONE_RIEGO")
    void conLluviaExactoUmbral_emitePostpone() {
        WeatherForecast forecast = new WeatherForecast(UMBRAL, 2.0, Instant.now());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.POSTPONE_RIEGO);
    }

    @Test
    @DisplayName("lluvia > umbral → POSTPONE_RIEGO con motivo descriptivo")
    void conLluviaSupeorUmbral_emitePostponeConMotivo() {
        WeatherForecast forecast = new WeatherForecast(85.0, 1.0, Instant.now());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        RuleAction accion = acciones.get(0);
        assertThat(accion.type()).isEqualTo(ActionType.POSTPONE_RIEGO);
        assertThat(accion.ruleName()).isEqualTo("WeatherOverrideRule");
        assertThat(accion.motivo()).contains("85").contains("60");
    }

    @Test
    @DisplayName("POSTPONE_RIEGO es una acción bloqueante")
    void postponeRiegoEsBloqueante() {
        assertThat(ActionType.POSTPONE_RIEGO.isBlocking()).isTrue();
    }
}
