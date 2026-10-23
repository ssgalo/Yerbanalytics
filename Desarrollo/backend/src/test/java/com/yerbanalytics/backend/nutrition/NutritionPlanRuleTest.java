package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.nutrition.NutritionTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("N-01 · plan de nutrición")
class NutritionPlanRuleTest {

    private NutritionPlanService plan;
    private NutritionPlanRule rule;
    private ZonaEntity zone;
    private List<SectorEntity> sectors;

    @BeforeEach
    void setUp() {
        plan = mock(NutritionPlanService.class);
        rule = new NutritionPlanRule(plan);
        zone = zone("MZ-2");
        sectors = sectors(zone, 100);
    }

    private List<RuleAction> evaluate(NutritionPlanFacts facts, ZoneRuleContext ctx) {
        when(plan.facts(zone)).thenReturn(facts);
        return rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private List<RuleAction> evaluate(NutritionPlanFacts facts) {
        return evaluate(facts, ctx(zone, sectors));
    }

    private static DetalleNutritionPlan.Change changeOf(List<RuleAction> actions) {
        return actions.stream().filter(a -> a.type() == ActionType.UPDATE_NUTRITION_PLAN)
                .map(a -> ((DetalleNutritionPlan) a.detalle()).change()).findFirst().orElseThrow();
    }

    @Test
    void conElIntervaloCumplidoPideLaTandaParaTodosLosSectoresYDejaUnInfo() {
        List<RuleAction> actions = evaluate(idle(7, 7));

        assertThat(changeOf(actions)).isEqualTo(DetalleNutritionPlan.Change.START);
        RuleAction alert = actions.stream().filter(a -> a.type() == ActionType.ALERTA).findFirst().orElseThrow();
        assertThat(((DetalleAlerta) alert.detalle()).nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(alert.motivo()).contains("MZ-2").contains("100 sector");
    }

    @Test
    void conCincoDiasDeSieteTodaviaNoActua() {
        List<RuleAction> actions = evaluate(idle(7, 5));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("Todavía no");
    }

    @Test
    void unaZonaQueNuncaRecibioElPlanSeConsideraVencida() {
        assertThat(changeOf(evaluate(idle(7, null)))).isEqualTo(DetalleNutritionPlan.Change.START);
    }

    @Test
    void elIntervaloPropioDeLaZonaManda() {
        // 14 días fijados para la zona: a los 10 todavía no toca.
        assertThat(evaluate(idle(14, 10))).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(changeOf(evaluate(idle(14, 14)))).isEqualTo(DetalleNutritionPlan.Change.START);
    }

    @Test
    void unPlanAdelantadoPorCeBajaVenceAunqueNoHayaPasadoElIntervalo() {
        assertThat(changeOf(evaluate(advanced(idle(10, 6))))).isEqualTo(DetalleNutritionPlan.Change.START);
    }

    @Test
    void conLaTandaAbiertaYSectoresEsperandoNoPideNadaNiDuplica() {
        List<RuleAction> actions = evaluate(batchOpen(idle(7, 7), 12));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("12 sector");
    }

    @Test
    void conLaTandaAbiertaYNingunoEsperandoLaCierra() {
        List<RuleAction> actions = evaluate(batchOpen(idle(7, 7), 0));

        assertThat(changeOf(actions)).isEqualTo(DetalleNutritionPlan.Change.COMPLETE);
        assertThat(has(actions, ActionType.ALERTA)).isFalse();
    }

    @Test
    void conLaZonaBloqueadaElPlanEsperaSinIniciarLaTanda() {
        ZoneRuleContext locked = new ZoneRuleContext(zone, sectors, NOW, Map.of(), Map.of(), true, Set.of(),
                RuleOverrides.NONE);

        List<RuleAction> actions = evaluate(idle(7, 7), locked);

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("bloqueo manual");
    }

    @Test
    void siNingunSectorPuedeDosificarseNoIniciaLaTanda() {
        ZoneRuleContext noPumps = new ZoneRuleContext(zone, sectors, NOW, Map.of(), Map.of(), false, Set.of(),
                RuleOverrides.NONE);

        List<RuleAction> actions = evaluate(idle(7, 7), noPumps);

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("ningún sector puede dosificarse");
    }

    @Test
    void losSectoresQueNoPuedenDosificarseSeAvisanComoExcluidosDeLaTanda() {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        hardware.put(sectors.get(0).getId(), HardwareCoverage.NONE);
        ZoneRuleContext ctx = new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), false, Set.of(),
                RuleOverrides.NONE);

        List<RuleAction> actions = evaluate(idle(7, 7), ctx);

        RuleAction alert = actions.stream().filter(a -> a.type() == ActionType.ALERTA).findFirst().orElseThrow();
        assertThat(alert.motivo()).contains("99 sector").contains("1 quedan fuera");
    }

    @Test
    void elCooldownIntervalDelSimuladorReemplazaElIntervaloDeLaZona() {
        TestRuleOverrides overrides = new TestRuleOverrides();
        overrides.put(NutritionPlanRule.NAME,
                new RuleOverride(true, Map.of(NutritionPlanRule.COOLDOWN_INTERVAL, 0.0), Set.of()));

        // Aplicado hace 1 día con intervalo de 7: con el cooldown en 0 vence ya.
        List<RuleAction> actions = evaluate(idle(7, 1), ctx(zone, sectors, overrides));

        assertThat(changeOf(actions)).isEqualTo(DetalleNutritionPlan.Change.START);
    }

    @Test
    void sinCooldownDelSimuladorUnDiaDeSieteNoAlcanza() {
        assertThat(evaluate(idle(7, 1))).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void laTrazaMarcaSinDatoLaUltimaAplicacionDeUnaZonaNueva() {
        when(plan.facts(zone)).thenReturn(idle(7, null));
        Evaluacion ev = new Evaluacion(rule, ReglaTestSupport.fabrica());

        rule.evaluate(ctx(zone, sectors), ev);

        assertThat(ev.comparaciones()).anySatisfy(c -> {
            assertThat(c.etiqueta()).contains("última aplicación");
            assertThat(c.resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
        });
    }

    @Test
    void esUnaReglaDeZonaControlableDesdeElSimulador() {
        assertThat(rule.name()).isEqualTo("NutritionPlanRule");
        assertThat(rule.cooldownKeys()).containsExactly("interval");
        assertThat(rule.bypassKeys()).isEmpty();
        assertThat(rule.parametros()).extracting(p -> p.clave()).containsExactly("nutricion.intervalo-plan");
        assertThat(rule.branch().name()).isEqualTo("INSUMO");
    }
}
