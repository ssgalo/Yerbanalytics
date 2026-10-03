package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.evV2;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PosponerPorLluviaRule (R-03)")
class PosponerPorLluviaRuleTest {

    private final PosponerPorLluviaRule rule = new PosponerPorLluviaRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    /** Pronóstico de las 4 horas siguientes a las 10:05 con la probabilidad máxima y los mm totales dados. */
    private static WeatherForecast lluvia(double probMax, double mmTotal) {
        return RiegoCtx.pronostico(RiegoCtx.instante("10:05"),
                new double[]{probMax, 10, 10, 10}, new double[]{mmTotal, 0, 0, 0});
    }

    private RuleContext ctx(Double h, WeatherForecast f) {
        return RiegoCtx.a("10:05").humedad(h).forecast(f).build();
    }

    @Test
    @DisplayName("(70 %, 5 mm) con h 40 → POSTPONE_RIEGO + ALERTA INFO")
    void ambosUmbralesEnElBorde() {
        List<RuleAction> acciones = rule.evaluate(ctx(40.0, lluvia(70, 5.0)), evV2(rule));

        assertThat(tipos(acciones)).containsExactlyInAnyOrder(ActionType.POSTPONE_RIEGO, ActionType.ALERTA);
        DetalleAlerta d = (DetalleAlerta) acciones.stream().filter(a -> a.type() == ActionType.ALERTA)
                .findFirst().orElseThrow().detalle();
        assertThat(d.nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(d.texto()).startsWith("Riego pospuesto por pronóstico de lluvia");
    }

    @Test
    @DisplayName("(95 %, 4,9 mm) → NOOP_INFO")
    void muchaProbabilidadPocosMm() {
        assertThat(tipos(rule.evaluate(ctx(40.0, lluvia(95, 4.9)), evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("(69 %, 20 mm) → NOOP_INFO")
    void muchosMmPocaProbabilidad() {
        assertThat(tipos(rule.evaluate(ctx(40.0, lluvia(69, 20.0)), evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("sin pronóstico → dos SIN_DATO y NOOP_INFO (no pospone)")
    void sinPronostico() {
        Evaluacion ev = evV2(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(40.0, null), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        List<Comparacion> lluvias = ev.comparaciones().stream()
                .filter(c -> c.clave().equals("riego.lluvia-probabilidad") || c.clave().equals("riego.lluvia-mm")).toList();
        assertThat(lluvias).hasSize(2).allMatch(c -> c.resultado() == ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("pronóstico sin marcas horarias (modo degradado) → SIN_DATO, no pospone")
    void pronosticoSinHoras() {
        WeatherForecast sinHoras = new WeatherForecast(90, 5, RiegoCtx.instante("10:05"), 22, "x", 70, List.of());
        Evaluacion ev = evV2(rule);

        assertThat(tipos(rule.evaluate(ctx(40.0, sinHoras), ev))).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).anyMatch(c -> c.resultado() == ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("probabilidad alta pero milímetros sin dato → la comparación de mm queda SIN_DATO y no pospone")
    void milimetrosSinDato() {
        WeatherForecast f = new WeatherForecast(90, 5, RiegoCtx.instante("10:05"), 22, "x", 70, List.of(), List.of(
                new WeatherForecast.PronosticoHora(java.time.LocalDateTime.of(RiegoCtx.DIA, java.time.LocalTime.of(11, 0)), 95.0, null)));
        Evaluacion ev = evV2(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(40.0, f), ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        Comparacion mm = ev.comparaciones().stream().filter(c -> "riego.lluvia-mm".equals(c.clave())).findFirst().orElseThrow();
        assertThat(mm.resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("h 60 con lluvia fuerte → no aplica, sin alerta")
    void sinDeficit_noAplica() {
        List<RuleAction> acciones = rule.evaluate(ctx(60.0, lluvia(90, 15.0)), evV2(rule));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("no aplica");
    }

    @Test
    @DisplayName("h 34 con lluvia fuerte → no aplica (R-02 gana)")
    void deficitCritico_noAplica() {
        List<RuleAction> acciones = rule.evaluate(ctx(34.0, lluvia(90, 12.0)), evV2(rule));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("no aplica");
    }

    @Test
    @DisplayName("lluvia-ventana 2: la lluvia de la hora +3 no cuenta")
    void ventanaCorta() {
        WeatherForecast f = RiegoCtx.pronostico(RiegoCtx.instante("10:05"),
                new double[]{10, 10, 90, 10}, new double[]{0, 0, 12, 0});

        List<RuleAction> acciones = rule.evaluate(ctx(40.0, f), evV2(rule, Map.of(ParametrosRiego.LLUVIA_VENTANA, "2")));

        assertThat(tipos(acciones)).containsExactly(ActionType.NOOP_INFO);
        // con la ventana de 4 h (fábrica) esa misma lluvia sí pospone
        assertThat(tipos(rule.evaluate(ctx(40.0, f), evV2(rule)))).contains(ActionType.POSTPONE_RIEGO);
    }

    @Test
    @DisplayName("la lluvia fuera de la ventana (hora +5) no cuenta")
    void lluviaFueraDeLaVentana() {
        WeatherForecast f = RiegoCtx.pronostico(RiegoCtx.instante("10:05"),
                new double[]{10, 10, 10, 10, 95, 95}, new double[]{0, 0, 0, 0, 10, 10});

        assertThat(tipos(rule.evaluate(ctx(40.0, f), evV2(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("la traza compara probabilidad máxima (≥ 70 %) y milímetros (≥ 5 mm)")
    void traza() {
        Evaluacion ev = evV2(rule);

        rule.evaluate(ctx(40.0, lluvia(70, 5.0)), ev);

        Comparacion prob = ev.comparaciones().stream().filter(c -> "riego.lluvia-probabilidad".equals(c.clave()))
                .findFirst().orElseThrow();
        assertThat(prob.recibido()).isEqualTo(70.0);
        assertThat(prob.operador()).isEqualTo(Operador.GE);
        assertThat(prob.umbral()).isEqualTo(70.0);
        Comparacion mm = ev.comparaciones().stream().filter(c -> "riego.lluvia-mm".equals(c.clave()))
                .findFirst().orElseThrow();
        assertThat(mm.recibido()).isEqualTo(5.0);
        assertThat(mm.operador()).isEqualTo(Operador.GE);
        assertThat(mm.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("metadatos: RIEGO, prioridad 8")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(8);
        assertThat(rule.parametros()).containsExactlyInAnyOrder(ParametrosRiego.LLUVIA_PROBABILIDAD,
                ParametrosRiego.LLUVIA_MM, ParametrosRiego.LLUVIA_VENTANA,
                ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
