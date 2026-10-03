package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
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

@DisplayName("DeficitCriticoRule (R-02)")
class DeficitCriticoRuleTest {

    private final DeficitCriticoRule rule = new DeficitCriticoRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    @Test
    @DisplayName("34 % → ACTIVAR_VALVULA con 6 L y 720 s, más ALERTA CRITICAL")
    void justoBajoElCritico() {
        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("23:30").humedad(34.0).build(), ev(rule));

        assertThat(tipos(acciones)).containsExactlyInAnyOrder(ActionType.ACTIVAR_VALVULA, ActionType.ALERTA);
        RuleAction riego = acciones.stream().filter(a -> a.type() == ActionType.ACTIVAR_VALVULA).findFirst().orElseThrow();
        assertThat(riego.ruleName()).isEqualTo("DeficitCriticoRule");
        DetalleRiego d = (DetalleRiego) riego.detalle();
        assertThat(d.volumenL()).isEqualTo(6.0);
        assertThat(d.duracionSeg()).isEqualTo(720);
        assertThat(d.humedad()).isEqualTo(34.0);
        assertThat(d.recortado()).isFalse();
        assertThat(riego.motivo()).contains("34").contains("720");
        DetalleAlerta a = (DetalleAlerta) acciones.stream().filter(x -> x.type() == ActionType.ALERTA)
                .findFirst().orElseThrow().detalle();
        assertThat(a.nivel()).isEqualTo(NivelAlerta.CRITICAL);
        assertThat(a.texto()).isEqualTo("Déficit hídrico crítico");
    }

    @Test
    @DisplayName("35 % (en el crítico) → NOOP_INFO: decide R-01")
    void enElCritico_noRiega() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(35.0).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.clave()).isEqualTo("riego.umbral-critico");
        assertThat(c.operador()).isEqualTo(Operador.LT);
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    @DisplayName("riega a cualquier hora (23:30) y no mira el pronóstico ni la pausa")
    void aCualquierHora_ignoraLluviaYPausa() {
        RuleContext ctx = RiegoCtx.a("23:30").humedad(20.0).ultimaAplicacionHace(1, 0, 0).build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).contains(ActionType.ACTIVAR_VALVULA);
    }

    @Test
    @DisplayName("último riego crítico hace 11 h 59 min → ABORT_RIEGO por el tope")
    void tope_cortaA11h59() {
        Evaluacion ev = ev(rule);
        RuleContext ctx = RiegoCtx.a("12:00").humedad(30.0).ultimoRiegoCriticoHace(11, 59).build();

        List<RuleAction> acciones = rule.evaluate(ctx, ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).contains("12");
        Comparacion tope = ev.comparaciones().get(1);
        assertThat(tope.clave()).isEqualTo("riego.exceptuado-bloqueo");
        assertThat(tope.operador()).isEqualTo(Operador.LT);
        assertThat(tope.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("último riego crítico hace 12 h exactas → riega")
    void tope_vencidoA12h() {
        RuleContext ctx = RiegoCtx.a("12:00").humedad(30.0).ultimoRiegoCriticoHace(12, 0).build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactlyInAnyOrder(ActionType.ACTIVAR_VALVULA, ActionType.ALERTA);
    }

    @Test
    @DisplayName("override del tope (6 h): hace 7 h riega")
    void tope_override() {
        Evaluacion ev = ev(rule, Map.of(ParametrosRiego.EXCEPTUADO_BLOQUEO, "6"));
        RuleContext ctx = RiegoCtx.a("12:00").humedad(30.0).ultimoRiegoCriticoHace(7, 0).build();

        assertThat(tipos(rule.evaluate(ctx, ev))).contains(ActionType.ACTIVAR_VALVULA);
    }

    @Test
    @DisplayName("sin humedad → SIN_DATO y NOOP_INFO")
    void sinHumedad() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("el volumen máximo y el caudal vienen del catálogo (override 3 L a 60 L/h → 180 s)")
    void volumenYCaudalDelCatalogo() {
        Evaluacion ev = ev(rule, Map.of(ParametrosRiego.VOLUMEN_MAX_EVENTO, "3", ParametrosRiego.CAUDAL_EMISOR, "60"));

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(30.0).build(), ev);

        DetalleRiego d = (DetalleRiego) acciones.stream().filter(a -> a.type() == ActionType.ACTIVAR_VALVULA)
                .findFirst().orElseThrow().detalle();
        assertThat(d.volumenL()).isEqualTo(3.0);
        assertThat(d.duracionSeg()).isEqualTo(180);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 4 y parámetros declarados")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(4);
        assertThat(rule.parametros()).containsExactlyInAnyOrder(ParametrosRiego.UMBRAL_CRITICO,
                ParametrosRiego.VOLUMEN_MAX_EVENTO, ParametrosRiego.CAUDAL_EMISOR, ParametrosRiego.EXCEPTUADO_BLOQUEO);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
