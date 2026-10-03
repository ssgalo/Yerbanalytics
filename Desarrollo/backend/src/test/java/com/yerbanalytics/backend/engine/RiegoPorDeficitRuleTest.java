package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
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

@DisplayName("RiegoPorDeficitRule (R-01)")
class RiegoPorDeficitRuleTest {

    private final RiegoPorDeficitRule rule = new RiegoPorDeficitRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    @Test
    @DisplayName("44 % → ACTIVAR_VALVULA con 4,2 L y 504 s, motivo legible y '44 % < 45 % ✓' en la traza")
    void justoDebajoDelUmbral() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(44.0).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ACTIVAR_VALVULA);
        RuleAction a = acciones.get(0);
        assertThat(a.ruleName()).isEqualTo("RiegoPorDeficitRule");
        DetalleRiego d = (DetalleRiego) a.detalle();
        assertThat(d.volumenL()).isEqualTo(4.2);
        assertThat(d.duracionSeg()).isEqualTo(504);
        assertThat(d.humedad()).isEqualTo(44.0);
        assertThat(a.motivo()).contains("44").contains("45").contains("504");
        Comparacion c = ev.comparaciones().stream().filter(x -> "riego.umbral-humedad".equals(x.clave())).findFirst().orElseThrow();
        assertThat(c.recibido()).isEqualTo(44.0);
        assertThat(c.operador()).isEqualTo(Operador.LT);
        assertThat(c.umbral()).isEqualTo(45.0);
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("44,9 % → 4,02 L y 483 s (ejemplo de v2)")
    void ejemploDeV2() {
        DetalleRiego d = (DetalleRiego) rule.evaluate(RiegoCtx.a("10:05").humedad(44.9).build(), ev(rule)).get(0).detalle();

        assertThat(d.volumenL()).isEqualTo(4.02);
        assertThat(d.duracionSeg()).isEqualTo(483);
    }

    @Test
    @DisplayName("35 % (en el crítico) → riega: decide R-01 con el tope de 6 L")
    void enElCritico_riegaR01() {
        DetalleRiego d = (DetalleRiego) rule.evaluate(RiegoCtx.a("10:05").humedad(35.0).build(), ev(rule)).get(0).detalle();

        assertThat(d.volumenL()).isEqualTo(6.0);
        assertThat(d.duracionSeg()).isEqualTo(720);
    }

    @Test
    @DisplayName("45 % (en el umbral) → NOOP_INFO con '45 % < 45 % ✗'")
    void enElUmbral_noRiega() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(45.0).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        Comparacion c = ev.comparaciones().stream().filter(x -> "riego.umbral-humedad".equals(x.clave())).findFirst().orElseThrow();
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    @DisplayName("34 % → NOOP_INFO, el motivo dice que lo cubre R-02")
    void deficitCritico_loCubreR02() {
        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(34.0).build(), ev(rule));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("R-02");
    }

    @Test
    @DisplayName("override del umbral 50 y h 48 → riega")
    void overrideDelUmbral() {
        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(48.0).build(),
                ev(rule, Map.of(ParametrosRiego.UMBRAL_HUMEDAD, "50")));

        assertThat(tipos(acciones)).containsExactly(ActionType.ACTIVAR_VALVULA);
    }

    @Test
    @DisplayName("litros por punto 0,3 y h 40 → 6 L (tope) y 720 s")
    void topeDeVolumen() {
        DetalleRiego d = (DetalleRiego) rule.evaluate(RiegoCtx.a("10:05").humedad(40.0).build(),
                ev(rule, Map.of(ParametrosRiego.LITROS_POR_PUNTO, "0.3"))).get(0).detalle();

        assertThat(d.volumenL()).isEqualTo(6.0);
        assertThat(d.duracionSeg()).isEqualTo(720);
    }

    @Test
    @DisplayName("un volumen que redondea a 0 L no emite ACTIVAR_VALVULA (nunca una orden de 0 s)")
    void planVacio_noRiega() {
        // Configuración incoherente (umbral 60 y objetivo 55, que el guardado rechaza): h 57 ya pasó el objetivo.
        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").humedad(57.0).build(),
                ev(rule, Map.of(ParametrosRiego.UMBRAL_HUMEDAD, "60", ParametrosRiego.HUMEDAD_OBJETIVO, "55")));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("0 L");
    }

    @Test
    @DisplayName("sin humedad → SIN_DATO y NOOP_INFO (no se riega a ciegas)")
    void sinHumedad() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("10:05").build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).allMatch(c -> c.resultado() == ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 10 y los seis parámetros que lee")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(10);
        assertThat(rule.parametros()).containsExactlyInAnyOrder(ParametrosRiego.UMBRAL_HUMEDAD,
                ParametrosRiego.UMBRAL_CRITICO, ParametrosRiego.HUMEDAD_OBJETIVO, ParametrosRiego.LITROS_POR_PUNTO,
                ParametrosRiego.VOLUMEN_MAX_EVENTO, ParametrosRiego.CAUDAL_EMISOR);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
