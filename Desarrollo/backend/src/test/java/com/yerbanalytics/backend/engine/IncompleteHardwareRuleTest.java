package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.rules.IncompleteHardwareRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-04: un sector (o una macro-zona, en la mediasombra) sin un actuador que su rama requiere no recibe acciones
 * autónomas de esa rama. Aborta sólo lo que falta, sin alertas nuevas.
 */
@DisplayName("IncompleteHardwareRule (S-04)")
class IncompleteHardwareRuleTest {

    private final IncompleteHardwareRule rule = new IncompleteHardwareRule();

    private static RuleContext ctx(HardwareCoverage hardware) {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-120");
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        sector.setZona(zona);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok",
                Instant.now(), null, false, ContextoRiego.vacio(), RuleOverrides.NONE, Set.of(), hardware);
    }

    private List<ActionType> tipos(HardwareCoverage hardware) {
        return rule.evaluate(ctx(hardware), ev(rule)).stream().map(RuleAction::type).toList();
    }

    @Test
    @DisplayName("hardware completo → NOOP_INFO 'completo' y la cadena sigue, con las comparaciones 'cumple'")
    void completo() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(HardwareCoverage.COMPLETE), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("completo");
        assertThat(ev.comparaciones()).hasSize(2)
                .allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE));
    }

    @Test
    @DisplayName("sector sin electroválvula → ABORT_RIEGO 'hardware incompleto'; no toca insumos ni mediasombra")
    void sinElectrovalvula() {
        List<RuleAction> acciones = rule.evaluate(ctx(new HardwareCoverage(false, true, true)), ev(rule));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("hardware incompleto")
                .contains("MZ-1-120").containsIgnoringCase("electroválvula");
    }

    @Test
    @DisplayName("zona sin nodo testigo → ABORT_RIEGO (sin lectura no hay con qué regar), con el motivo correcto")
    void sinNodoTestigo() {
        List<RuleAction> acciones = rule.evaluate(ctx(new HardwareCoverage(true, true, false)), ev(rule));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("nodo testigo");
    }

    @Test
    @DisplayName("sector sin bomba → ABORT_INSUMO; el riego del sector sí procede")
    void sinBomba() {
        assertThat(tipos(new HardwareCoverage(true, false, true))).containsExactly(ActionType.ABORT_INSUMO);
    }

    @Test
    @DisplayName("sin nada mapeado se abortan riego e insumos, nunca ABORT_ALL (la malla es de la zona)")
    void sinNada() {
        assertThat(tipos(HardwareCoverage.NONE))
                .containsExactly(ActionType.ABORT_RIEGO, ActionType.ABORT_INSUMO)
                .doesNotContain(ActionType.ABORT_ALL);
    }

    @Test
    @DisplayName("no emite alertas: ya se avisó al configurar el hardware")
    void sinAlertas() {
        assertThat(tipos(HardwareCoverage.NONE)).doesNotContain(ActionType.ALERTA);
        assertThat(rule.evaluate(ctx(HardwareCoverage.NONE), ev(rule))).allSatisfy(a -> assertThat(a.detalle()).isNull());
    }

    @Test
    @DisplayName("la traza registra una comparación por alcance y marca 'no cumple' sólo la que falta")
    void trazaPorAlcance() {
        Evaluacion ev = ev(rule);

        rule.evaluate(ctx(new HardwareCoverage(true, false, true)), ev);

        assertThat(ev.comparaciones()).extracting(c -> c.resultado()).containsExactly(
                ResultadoComparacion.CUMPLE, ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    @DisplayName("un contexto que no arma la cobertura se lee como completo (las demás reglas no cambian)")
    void porDefectoCompleto() {
        RuleContext sinCobertura = RuleContextTestFactory.basico(
                RuleContextTestFactory.sectorBasico("S"), RuleContextTestFactory.zonaBasica("Z"));

        assertThat(sinCobertura.hardware()).isEqualTo(HardwareCoverage.COMPLETE);
        assertThat(rule.evaluate(sinCobertura, ev(rule))).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    // ------------------------------------------------------------------ simulador

    @Test
    @DisplayName("bypass 'hardware': el sector sin electroválvula se evalúa como si estuviera completo")
    void bypassHardware() {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(IncompleteHardwareRule.NAME, new RuleOverride(true, Map.of(), Set.of("hardware")));
        RuleContext conBypass = ctx(HardwareCoverage.NONE).withOverrides(store);

        assertThat(rule.evaluate(conBypass, ev(rule))).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("metadatos: entre el bloqueo manual y la frescura, rama global, controlable desde el simulador")
    void metadatos() {
        assertThat(rule).isInstanceOf(Rule.class).isInstanceOf(DemoControllable.class);
        assertThat(rule.name()).isEqualTo("IncompleteHardwareRule");
        assertThat(rule.priority()).isEqualTo(1);
        assertThat(rule.branch()).isEqualTo(RuleBranch.GLOBAL);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
        assertThat(rule.bypassKeys()).containsExactly("hardware");
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.parametros()).isEmpty();
        assertThat(rule.usaDiagnostico()).isFalse();
    }
}
