package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.evV2;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FueraDeVentanaRiegoRule (R-05)")
class FueraDeVentanaRiegoRuleTest {

    private final FueraDeVentanaRiegoRule rule = new FueraDeVentanaRiegoRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    @ParameterizedTest(name = "h 40 a las {0} → {1}")
    @CsvSource({
            "05:59:59, ABORT_RIEGO",
            "06:00:00, NOOP_INFO",
            "17:59:59, NOOP_INFO",
            "18:00:00, NOOP_INFO",
            "18:00:59, NOOP_INFO",
            "18:01:00, ABORT_RIEGO",
            "23:30:00, ABORT_RIEGO",
            "00:00:00, ABORT_RIEGO"
    })
    @DisplayName("bordes de la ventana 06:00-18:00 (cerrada al minuto)")
    void bordesDeLaVentana(String hora, ActionType esperado) {
        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a(hora).humedad(40.0).build(), evV2(rule));

        assertThat(tipos(acciones)).containsExactly(esperado);
    }

    @Test
    @DisplayName("la traza muestra 'Hora local 17:42 ∈ 06:00-18:00' con la clave de la ventana")
    void trazaDeLaVentana() {
        Evaluacion ev = evV2(rule);

        rule.evaluate(RiegoCtx.a("17:42").humedad(40.0).build(), ev);

        Comparacion c = ev.comparaciones().stream().filter(x -> x.operador() == Operador.EN).findFirst().orElseThrow();
        assertThat(c.clave()).isEqualTo("riego.ventana-normal");
        assertThat(c.recibido()).isEqualTo("17:42");
        assertThat(c.umbral()).isEqualTo("06:00-18:00");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("la hora se toma en la zona del vivero (20:30 UTC = 17:30 locales) no en la del JVM")
    void usaLaZonaDelVivero() {
        // 2026-10-05T20:30:00Z es 17:30 en Buenos Aires: adentro de la ventana, aunque UTC diga 20:30.
        RuleContext ctx = RiegoCtx.a("17:30").humedad(40.0).build();
        assertThat(ctx.now().toString()).isEqualTo("2026-10-05T20:30:00Z");

        assertThat(tipos(rule.evaluate(ctx, evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ventana modificada 07:00-17:00 y 06:30 con 40 % → ABORT_RIEGO con '06:30 ∈ 07:00-17:00 ✗'")
    void ventanaModificada() {
        Evaluacion ev = evV2(rule, Map.of(ParametrosRiego.VENTANA_NORMAL, "07:00-17:00"));

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("06:30").humedad(40.0).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        Comparacion c = ev.comparaciones().stream().filter(x -> x.operador() == Operador.EN).findFirst().orElseThrow();
        assertThat(c.recibido()).isEqualTo("06:30");
        assertThat(c.umbral()).isEqualTo("07:00-17:00");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @ParameterizedTest(name = "h {0} a las 23:30 → no aplica")
    @CsvSource({"34", "45", "50", "80"})
    @DisplayName("sin déficit común (h < crítico o h ≥ umbral) no aplica: lo cubre R-02 o no hay déficit")
    void noAplica(double h) {
        Evaluacion ev = evV2(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("23:30").humedad(h).build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("no aplica");
        assertThat(ev.comparaciones()).noneMatch(x -> x.operador() == Operador.EN);
    }

    @Test
    @DisplayName("35 % (en el crítico) ya es déficit común: a las 23:30 corta")
    void enElCritico_aplicaYCorta() {
        assertThat(tipos(rule.evaluate(RiegoCtx.a("23:30").humedad(35.0).build(), evV2(rule))))
                .containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("sin humedad → no aplica (SIN_DATO) y NOOP_INFO")
    void sinHumedad() {
        Evaluacion ev = evV2(rule);

        List<RuleAction> acciones = rule.evaluate(RiegoCtx.a("23:30").build(), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 6, ventana + los dos umbrales compartidos")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(6);
        assertThat(rule.parametros()).containsExactlyInAnyOrder(ParametrosRiego.VENTANA_NORMAL,
                ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
