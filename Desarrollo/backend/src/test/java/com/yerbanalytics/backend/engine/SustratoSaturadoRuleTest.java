package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.SustratoSaturadoRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SustratoSaturadoRule (R-04)")
class SustratoSaturadoRuleTest {

    private final SustratoSaturadoRule rule = new SustratoSaturadoRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    private List<RuleAction> con(Double h) {
        return rule.evaluate(RiegoCtx.a("10:05").humedad(h).build(), ev(rule));
    }

    @Test
    @DisplayName("74 % → NOOP_INFO")
    void bajoElBloqueo() {
        assertThat(tipos(con(74.0))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("75 % (borde) → ABORT_RIEGO y sin alerta")
    void enElBloqueo_sinAlerta() {
        List<RuleAction> acciones = con(75.0);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).contains("75");
    }

    @Test
    @DisplayName("79 % → todavía sin alerta")
    void justoBajoLaAlerta() {
        assertThat(tipos(con(79.0))).containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("80 % (borde) → ABORT_RIEGO y ALERTA WARNING de la macro-zona")
    void enLaAlerta() {
        List<RuleAction> acciones = con(80.0);

        assertThat(tipos(acciones)).containsExactlyInAnyOrder(ActionType.ABORT_RIEGO, ActionType.ALERTA);
        RuleAction alerta = acciones.stream().filter(a -> a.type() == ActionType.ALERTA).findFirst().orElseThrow();
        assertThat(alerta.ruleName()).isEqualTo("SustratoSaturadoRule");
        assertThat(alerta.detalle()).isInstanceOf(DetalleAlerta.class);
        DetalleAlerta d = (DetalleAlerta) alerta.detalle();
        assertThat(d.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(d.texto()).isEqualTo("Sustrato saturado, riesgo de asfixia radicular y hongos");
    }

    @Test
    @DisplayName("override de bloqueo 70: 72 % bloquea")
    void overrideDelBloqueo() {
        Evaluacion ev = ev(rule, Map.of(ParametrosRiego.SATURACION_BLOQUEO, "70"));

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(72.0).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("override de alerta 78: 78 % alerta")
    void overrideDeLaAlerta() {
        Evaluacion ev = ev(rule, Map.of(ParametrosRiego.SATURACION_ALERTA, "78"));

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(78.0).build(), ev);

        assertThat(tipos(acciones)).containsExactlyInAnyOrder(ActionType.ABORT_RIEGO, ActionType.ALERTA);
    }

    @Test
    @DisplayName("sin lectura de humedad → SIN_DATO y NOOP_INFO (no bloquea)")
    void sinHumedad() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("la traza registra la humedad contra el bloqueo (≥) y, si bloquea, contra la alerta")
    void traza() {
        Evaluacion ev = ev(rule);

        rule.evaluate(RiegoCtx.a("10:05").humedad(76.0).build(), ev);

        assertThat(ev.comparaciones()).hasSize(2);
        Comparacion bloqueo = ev.comparaciones().get(0);
        assertThat(bloqueo.clave()).isEqualTo("riego.saturacion-bloqueo");
        assertThat(bloqueo.operador()).isEqualTo(Operador.GE);
        assertThat(bloqueo.umbral()).isEqualTo(75.0);
        assertThat(bloqueo.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        Comparacion alerta = ev.comparaciones().get(1);
        assertThat(alerta.clave()).isEqualTo("riego.saturacion-alerta");
        assertThat(alerta.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 3, declara bloqueo y alerta")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(3);
        assertThat(rule.parametros()).containsExactly(ParametrosRiego.SATURACION_BLOQUEO, ParametrosRiego.SATURACION_ALERTA);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
