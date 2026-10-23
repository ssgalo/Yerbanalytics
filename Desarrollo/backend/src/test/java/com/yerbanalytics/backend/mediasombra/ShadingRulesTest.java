package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleMediasombra;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de la mediasombra, que es de la macro-zona: {@link ShadeFaultRule} (la guarda) y {@link ShadingRule}. */
class ShadingRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private static ZoneRuleContext ctx(boolean bloqueada, MallaZona malla, WeatherForecast forecast,
                                       RuleOverrides overrides) {
        ZonaEntity zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        return new ZoneRuleContext(zona, List.of(), NOW, Map.of(), Map.of(), bloqueada, Set.of(), overrides, forecast,
                malla);
    }

    private static ZoneRuleContext ctx(MallaZona malla) {
        return ctx(false, malla, null, RuleOverrides.NONE);
    }

    private static WeatherForecast uv(double indice) {
        return new WeatherForecast(10.0, indice, NOW, 20.0, "Soleado", 40.0, List.of());
    }

    private static Evaluacion ev(Object rule) {
        return new Evaluacion((com.yerbanalytics.backend.engine.parametros.ConsumidorParametros) rule,
                ReglaTestSupport.fabrica());
    }

    /** Overrides con un bypass encendido para una regla. */
    private static RuleOverrides bypass(String regla, String clave) {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(regla, new RuleOverride(Map.of(), Set.of(clave)));
        return store;
    }

    @Nested
    @DisplayName("ShadeFaultRule (guarda de la rama, por macro-zona)")
    class Guarda {

        private final ShadeFaultRule rule = new ShadeFaultRule();

        @Test
        @DisplayName("malla registrada y sana, sin bloqueo: no corta")
        void operativa() {
            List<RuleAction> acciones = rule.evaluate(ctx(MallaZona.OPERATIVA), ev(rule));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        }

        @Test
        @DisplayName("bloqueo manual de la zona (S-01): corta la rama")
        void bloqueo() {
            List<RuleAction> acciones = rule.evaluate(ctx(true, MallaZona.OPERATIVA, null, RuleOverrides.NONE), ev(rule));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_MEDIASOMBRA);
            assertThat(acciones.get(0).motivo()).contains("Bloqueo manual");
        }

        @Test
        @DisplayName("sin mediasombra registrada (S-04): corta la rama")
        void sinMalla() {
            List<RuleAction> acciones = rule.evaluate(ctx(new MallaZona(false, null)), ev(rule));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_MEDIASOMBRA);
            assertThat(acciones.get(0).motivo()).contains("no tiene mediasombra registrada");
        }

        @Test
        @DisplayName("malla averiada (S-05): corta la rama y dice la falla")
        void falla() {
            List<RuleAction> acciones = rule.evaluate(ctx(new MallaZona(true, "MEDIASOMBRA_FIN_DE_CARRERA")), ev(rule));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_MEDIASOMBRA);
            assertThat(acciones.get(0).motivo()).contains("Falla mecánica");
        }

        @Test
        @DisplayName("los bypass del simulador ignoran la falla y la falta de malla")
        void bypasses() {
            ZoneRuleContext conFalla = ctx(false, new MallaZona(true, "MEDIASOMBRA_FIN_DE_CARRERA"), null,
                    bypass(ShadeFaultRule.NAME, ShadeFaultRule.BYPASS_FAULT));
            ZoneRuleContext sinMalla = ctx(false, new MallaZona(false, null), null,
                    bypass(ShadeFaultRule.NAME, ShadeFaultRule.BYPASS_HARDWARE));
            assertThat(rule.evaluate(conFalla, ev(rule)).get(0).type()).isEqualTo(ActionType.NOOP_INFO);
            assertThat(rule.evaluate(sinMalla, ev(rule)).get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        }

        @Test
        @DisplayName("es de la rama MEDIASOMBRA y corre antes que ShadingRule")
        void ordenYRama() {
            assertThat(rule.branch()).isEqualTo(RuleBranch.MEDIASOMBRA);
            assertThat(rule.priority()).isLessThan(new ShadingRule().priority());
        }
    }

    @Nested
    @DisplayName("ShadingRule (lo reactivo: pico de UV)")
    class PicoUv {

        private final ShadingRule rule = new ShadingRule();

        @Test
        @DisplayName("pico de UV: ordena cerrar la malla de la zona")
        void cierra() {
            List<RuleAction> acciones = rule.evaluate(ctx(false, MallaZona.OPERATIVA, uv(9.5), RuleOverrides.NONE), ev(rule));
            assertThat(acciones).hasSize(1);
            assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
            assertThat(acciones.get(0).detalle()).isEqualTo(new DetalleMediasombra(false));
        }

        @Test
        @DisplayName("pico de UV: la reenvía aunque la última orden ya haya sido cerrar")
        void reenvia() {
            ZoneRuleContext c = ctx(false, MallaZona.OPERATIVA, uv(9.5), RuleOverrides.NONE);
            c.zone().setMediasombraAbierta(false);
            assertThat(rule.evaluate(c, ev(rule)).get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        }

        @Test
        @DisplayName("sin pico: no mueve la malla (la maneja el horario, no el motor)")
        void sinPico() {
            List<RuleAction> acciones = rule.evaluate(ctx(false, MallaZona.OPERATIVA, uv(3.0), RuleOverrides.NONE), ev(rule));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
            assertThat(acciones.get(0).motivo()).contains("horario");
        }

        @Test
        @DisplayName("declara su único parámetro y registra el UV contra el umbral")
        void registraElUv() {
            Evaluacion ev = ev(rule);
            rule.evaluate(ctx(false, MallaZona.OPERATIVA, uv(9.5), RuleOverrides.NONE), ev);

            assertThat(rule.parametros()).containsExactly(ParametrosMediasombra.UV_UMBRAL);
            assertThat(ev.comparaciones()).hasSize(1);
            assertThat(ev.comparaciones().get(0).recibido()).isEqualTo(9.5);
            assertThat(ev.comparaciones().get(0).operador()).isEqualTo(Operador.GE);
            assertThat(ev.comparaciones().get(0).umbral()).isEqualTo(7.0);
            assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        }

        @Test
        @DisplayName("sin pronóstico el UV queda SIN_DATO")
        void sinPronostico() {
            Evaluacion ev = ev(rule);
            rule.evaluate(ctx(MallaZona.OPERATIVA), ev);
            assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
        }

        @Test
        @DisplayName("el umbral UV sale del catálogo")
        void umbralDelCatalogo() {
            Evaluacion ev = new Evaluacion(rule, ReglaTestSupport.con(Map.of(ParametrosMediasombra.UV_UMBRAL, "9")));
            List<RuleAction> acciones = rule.evaluate(ctx(false, MallaZona.OPERATIVA, uv(8.0), RuleOverrides.NONE), ev);
            assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
        }
    }
}
