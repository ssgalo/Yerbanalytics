package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CicloLecturaRiegoRule (a lo sumo un riego por sector y ciclo)")
class CicloLecturaRiegoRuleTest {

    private final CicloLecturaRiegoRule rule = new CicloLecturaRiegoRule();

    private static List<ActionType> tipos(List<RuleAction> acciones) {
        return acciones.stream().map(RuleAction::type).toList();
    }

    @Test
    @DisplayName("regó a las 10:12 y se evalúa a las 13:59 (mismo ciclo 10:00-14:00) → ABORT_RIEGO")
    void mismoCiclo_aborta() {
        RuleContext ctx = RiegoCtx.a("13:59").humedad(40.0).inicioCiclo("10:00").ultimoRiego("10:12").build();
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx, ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).ruleName()).isEqualTo("CicloLecturaRiegoRule");
        assertThat(acciones.get(0).motivo()).contains("10:12").contains("10:00");
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        assertThat(c.configurable()).isFalse();
    }

    @Test
    @DisplayName("a las 14:00:10 el ciclo cambió (inicia 14:00) → NOOP_INFO")
    void cicloSiguiente_noAborta() {
        RuleContext ctx = RiegoCtx.a("14:00:10").humedad(40.0).inicioCiclo("14:00").ultimoRiego("10:12").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ya regó en el ciclo pero hay déficit crítico (h 30): NO corta, R-02 sólo tiene su tope")
    void yaRegadoConDeficitCritico_noAborta() {
        RuleContext ctx = RiegoCtx.a("13:30").humedad(30.0).inicioCiclo("10:00").ultimoRiego("10:12").build();
        Evaluacion ev = ev(rule);

        assertThat(tipos(rule.evaluate(ctx, ev))).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).extracting(Comparacion::resultado).contains(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("ya regó en el ciclo y no hay lectura de humedad: corta (sin dato no se arriesga)")
    void yaRegadoSinHumedad_aborta() {
        RuleContext ctx = RiegoCtx.a("13:59").inicioCiclo("10:00").ultimoRiego("10:12").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("riego en curso con déficit crítico (h 30): corta igual, nadie abre una válvula abierta")
    void riegoEnCursoConDeficitCritico_aborta() {
        RuleContext ctx = RiegoCtx.a("13:30").humedad(30.0).inicioCiclo("10:00").riegoEnCursoHasta("13:40").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("riego despachado justo en el inicio del ciclo cuenta como de este ciclo")
    void riegoEnElInstanteDeInicio_cuenta() {
        RuleContext ctx = RiegoCtx.a("10:30").humedad(40.0).inicioCiclo("10:00").ultimoRiego("10:00").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.ABORT_RIEGO);
    }

    @Test
    @DisplayName("riego en curso de un ciclo anterior → ABORT_RIEGO")
    void riegoEnCurso_aborta() {
        RuleContext ctx = RiegoCtx.a("14:05").humedad(40.0).inicioCiclo("14:00").ultimoRiego("13:50")
                .riegoEnCursoHasta("14:10").build();
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx, ev);

        assertThat(tipos(acciones)).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).contains("en curso").contains("14:10");
        assertThat(ev.comparaciones()).hasSize(2);
        assertThat(ev.comparaciones().get(1).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("un riego en curso que ya terminó no bloquea")
    void riegoEnCursoVencido_noBloquea() {
        RuleContext ctx = RiegoCtx.a("14:05").humedad(40.0).inicioCiclo("14:00").riegoEnCursoHasta("14:05").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("sector que nunca regó → NOOP_INFO")
    void sinRiegos_noAborta() {
        RuleContext ctx = RiegoCtx.a("10:05").humedad(40.0).inicioCiclo("10:00").build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ContextoRiego.vacio() (barrido, tests viejos) → NOOP_INFO")
    void contextoVacio_noAborta() {
        RuleContext ctx = RiegoCtx.a("10:05").humedad(40.0).build();

        assertThat(tipos(rule.evaluate(ctx, ev(rule)))).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("es una regla de la rama RIEGO; sólo lee el umbral crítico del catálogo (para dejar pasar a R-02)")
    void metadatos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(2);
        assertThat(rule.parametros()).containsExactly(com.yerbanalytics.backend.engine.parametros.ParametrosRiego.UMBRAL_CRITICO);
        assertThat(rule.name()).isEqualTo("CicloLecturaRiegoRule");
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
    }
}
