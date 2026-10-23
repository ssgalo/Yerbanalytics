package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("N-02 · CE baja")
class LowEcRuleTest {

    private NutritionPlanService plan;
    private ReadingHistory history;
    private LowEcRule rule;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        plan = mock(NutritionPlanService.class);
        history = mock(ReadingHistory.class);
        rule = new LowEcRule(plan, history);
        zone = zone("MZ-2");
    }

    private static List<ZoneReadingEntity> readings(Double... ce) {
        return java.util.Arrays.stream(ce).map(v -> {
            ZoneReadingEntity r = new ZoneReadingEntity();
            r.setZoneId("MZ-2");
            r.setCe(v);
            return r;
        }).toList();
    }

    private static List<ZoneReadingEntity> sixOf(double ce) {
        return IntStream.range(0, 6).mapToObj(i -> readings(ce).get(0)).toList();
    }

    private List<RuleAction> evaluate(NutritionPlanFacts facts, List<ZoneReadingEntity> latest) {
        when(plan.facts(zone)).thenReturn(facts);
        when(history.latest(eq("MZ-2"), anyInt(), anyBoolean())).thenReturn(latest);
        return rule.evaluate(ctx(zone, sectors(zone, 3)), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    @Test
    void ceBajaSinFertilizarAdelantaElPlanYDejaElInfo() {
        // Media 0,4 dS/m, última fertilización hace 6 días e intervalo del plan de 10.
        List<RuleAction> actions = evaluate(withLastFertilization(idle(10, 6), 6), sixOf(0.4));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.UPDATE_NUTRITION_PLAN, ActionType.ALERTA);
        assertThat(((DetalleNutritionPlan) actions.get(0).detalle()).change()).isEqualTo(DetalleNutritionPlan.Change.ADVANCE);
        DetalleAlerta alert = (DetalleAlerta) actions.get(1).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(alert.texto()).startsWith("CE baja: la fertilización de base del sustrato podría estar agotada");
    }

    @Test
    void siFertilizoHaceTresDiasNoActua() {
        List<RuleAction> actions = evaluate(withLastFertilization(idle(10, 3), 3), sixOf(0.4));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("se fertilizó hace poco");
    }

    @Test
    void conSoloCuatroLecturasLaTrazaMarcaSinDatoYNoActua() {
        when(plan.facts(zone)).thenReturn(idle(10, 6));
        when(history.latest(eq("MZ-2"), anyInt(), anyBoolean())).thenReturn(readings(0.4, 0.4, 0.4, 0.4));
        Evaluacion ev = new Evaluacion(rule, ReglaTestSupport.fabrica());

        List<RuleAction> actions = rule.evaluate(ctx(zone, sectors(zone, 3)), ev);

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("Sin dato").contains("4 lectura").contains("6");
    }

    @Test
    void lasLecturasSinCeNoCuentanParaLaMedia() {
        List<RuleAction> actions = evaluate(idle(10, 6), readings(0.4, null, 0.4, null, 0.4, 0.4));

        assertThat(actions.get(0).motivo()).contains("Sin dato").contains("4 lectura");
    }

    @Test
    void conLaCeEnSuRangoNoActua() {
        List<RuleAction> actions = evaluate(idle(10, 6), sixOf(0.9));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("no está por debajo");
    }

    @Test
    void elUmbralEsEstrictoUnaCeIgualA05NoEsBaja() {
        assertThat(evaluate(idle(10, 6), sixOf(0.5))).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void usaLaMediaDeLasLecturasNoLaUltima() {
        // Cinco bajas y una alta: media 0,48 < 0,5.
        List<RuleAction> actions = evaluate(idle(10, 6), readings(0.4, 0.4, 0.4, 0.4, 0.4, 0.8));

        assertThat(has(actions, ActionType.UPDATE_NUTRITION_PLAN)).isTrue();
    }

    @Test
    void unaSolaAlertaSiElPlanYaEstaAdelantado() {
        List<RuleAction> actions = evaluate(advanced(idle(10, 6)), sixOf(0.4));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("ya está adelantado");
    }

    @Test
    void unaSolaAlertaSiLaTandaEstaEnCurso() {
        assertThat(evaluate(batchOpen(idle(10, 6), 5), sixOf(0.4)))
                .extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void unaZonaQueNuncaFertilizoCumpleLaCondicionDeSinFertilizar() {
        List<RuleAction> actions = evaluate(withLastFertilization(idle(10, null), null), sixOf(0.4));

        assertThat(has(actions, ActionType.UPDATE_NUTRITION_PLAN)).isTrue();
    }

    @Test
    void cuentaCualquierFertilizacionDeLaZonaSeaDelPlanOCorrectiva() {
        // El plan se aplicó hace 6 días pero hubo una correctiva hace 2: lastFertilization manda.
        List<RuleAction> actions = evaluate(withLastFertilization(idle(10, 6), 2), sixOf(0.4));

        assertThat(has(actions, ActionType.UPDATE_NUTRITION_PLAN)).isFalse();
    }

    @Test
    void conElBypassSamplesUsaLaUltimaLecturaComoMedia() {
        TestRuleOverrides overrides = new TestRuleOverrides();
        overrides.put(LowEcRule.NAME, new RuleOverride(Map.of(), Set.of(LowEcRule.BYPASS_SAMPLES)));
        when(plan.facts(zone)).thenReturn(withLastFertilization(idle(10, 6), 6));
        when(history.latest(eq("MZ-2"), eq(1), anyBoolean())).thenReturn(readings(0.3));

        List<RuleAction> actions = rule.evaluate(ctx(zone, sectors(zone, 3), overrides),
                new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.UPDATE_NUTRITION_PLAN)).isTrue();
        // Con el bypass cuenta la última lectura aunque no haya respondido a un pulso.
        verify(history).latest("MZ-2", 1, false);
    }

    @Test
    void sinBypassSoloCuentanLasLecturasDePulso() {
        evaluate(idle(10, 6), sixOf(0.4));

        verify(history).latest("MZ-2", 6, true);
    }

    @Test
    void elCooldownNoFertilizedEnCeroEliminaLaEsperaDeCincoDias() {
        TestRuleOverrides overrides = new TestRuleOverrides();
        overrides.put(LowEcRule.NAME,
                new RuleOverride(Map.of(LowEcRule.COOLDOWN_NO_FERTILIZED, 0.0), Set.of()));
        when(plan.facts(zone)).thenReturn(withLastFertilization(idle(10, 1), 1));
        when(history.latest(eq("MZ-2"), anyInt(), anyBoolean())).thenReturn(sixOf(0.4));

        List<RuleAction> actions = rule.evaluate(ctx(zone, sectors(zone, 3), overrides),
                new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.UPDATE_NUTRITION_PLAN)).isTrue();
    }

    @Test
    void laTrazaRegistraLaMediaContraElUmbralDelCatalogo() {
        when(plan.facts(zone)).thenReturn(idle(10, 6));
        when(history.latest(eq("MZ-2"), anyInt(), anyBoolean())).thenReturn(sixOf(0.4));
        Evaluacion ev = new Evaluacion(rule, ReglaTestSupport.fabrica());

        rule.evaluate(ctx(zone, sectors(zone, 3)), ev);

        assertThat(ev.comparaciones()).anySatisfy(c -> {
            assertThat(c.clave()).isEqualTo("nutricion.ce-baja");
            assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        });
    }

    @Test
    void esUnaReglaDeZonaControlableDesdeElSimulador() {
        assertThat(rule.name()).isEqualTo("LowEcRule");
        assertThat(rule.cooldownKeys()).containsExactly("no-fertilized");
        assertThat(rule.bypassKeys()).containsExactly("samples");
        assertThat(rule.parametros()).extracting(p -> p.clave())
                .containsExactly("nutricion.ce-baja", "nutricion.ce-lecturas", "nutricion.sin-fertilizar-dias");
        assertThat(rule.priority()).isLessThan(new NutritionPlanRule(plan).priority());
    }
}
