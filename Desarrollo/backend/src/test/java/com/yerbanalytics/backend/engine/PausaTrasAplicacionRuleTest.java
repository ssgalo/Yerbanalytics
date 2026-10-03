package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.evV2;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PausaTrasAplicacionRule (R-06)")
class PausaTrasAplicacionRuleTest {

    private final PausaTrasAplicacionRule rule = new PausaTrasAplicacionRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    @Test
    @DisplayName("aplicación hace 5 h 59 min 59 s → ABORT_RIEGO del sector")
    void dentroDeLaPausa() {
        Evaluacion ev = evV2(rule);
        RuleContext ctx = RiegoCtx.a("11:00").humedad(40.0).ultimaAplicacionHace(5, 59, 59).build();

        List<RuleAction> acciones = rule.evaluate(ctx, ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        Comparacion c = ev.comparaciones().get(ev.comparaciones().size() - 1);
        assertThat(c.clave()).isEqualTo("riego.pausa-tras-aplicacion");
        assertThat(c.operador()).isEqualTo(Operador.LT);
        assertThat(c.umbral()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("aplicación hace exactamente 6 h → NOOP_INFO (pausa cumplida)")
    void pausaCumplida() {
        RuleContext ctx = RiegoCtx.a("11:00").humedad(40.0).ultimaAplicacionHace(6, 0, 0).build();

        assertThat(tipos(rule.evaluate(ctx, evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("sin aplicaciones → NOOP_INFO")
    void sinAplicaciones() {
        assertThat(tipos(rule.evaluate(RiegoCtx.a("11:00").humedad(40.0).build(), evV2(rule))))
                .containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("h 34 con aplicación reciente → no aplica (R-02 sí puede regar)")
    void conDeficitCritico_noAplica() {
        RuleContext ctx = RiegoCtx.a("11:00").humedad(34.0).ultimaAplicacionHace(1, 0, 0).build();

        List<RuleAction> acciones = rule.evaluate(ctx, evV2(rule));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("no aplica");
    }

    @Test
    @DisplayName("h 50 (sin déficit) con aplicación reciente → no aplica")
    void sinDeficit_noAplica() {
        RuleContext ctx = RiegoCtx.a("11:00").humedad(50.0).ultimaAplicacionHace(1, 0, 0).build();

        assertThat(tipos(rule.evaluate(ctx, evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("override de la pausa (2 h): aplicación hace 3 h no bloquea; con 24 h sí")
    void overrideDeLaPausa() {
        RuleContext ctx = RiegoCtx.a("11:00").humedad(40.0).ultimaAplicacionHace(3, 0, 0).build();

        assertThat(tipos(rule.evaluate(ctx, evV2(rule, Map.of(ParametrosRiego.PAUSA_TRAS_APLICACION, "2")))))
                .containsExactly(ActionType.NOOP_INFO);
        assertThat(tipos(rule.evaluate(ctx, evV2(rule, Map.of(ParametrosRiego.PAUSA_TRAS_APLICACION, "24")))))
                .containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 7")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(7);
        assertThat(rule.parametros()).containsExactlyInAnyOrder(ParametrosRiego.PAUSA_TRAS_APLICACION,
                ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
