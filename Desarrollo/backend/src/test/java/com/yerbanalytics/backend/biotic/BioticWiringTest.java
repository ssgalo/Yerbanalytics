package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import com.yerbanalytics.backend.supply.ApplicationPreconditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque real: las reglas bióticas quedan conectadas al motor por sector, al motor de zonas y al control de demo. Sólo
 * lectura: no ensucia la base.
 */
@SpringBootTest
@DisplayName("Reglas bióticas · conexión en el contexto real")
class BioticWiringTest {

    @Autowired
    private RuleOrchestrator orchestrator;

    @Autowired
    private ZoneRuleEngine zoneEngine;

    @Autowired
    private DemoControllableRegistry registry;

    @Autowired
    private List<DemoControllable> controllables;

    @Test
    @DisplayName("F-F1 y F-P1 corren en la cadena por sector, en la rama de insumos (la mediasombra es de la zona desde #66)")
    void sectorRulesAreInTheChain() {
        List<String> names = orchestrator.getRules().stream().map(Rule::name).toList();

        assertThat(names).contains("FungalDamageRule", "FoliarPestRule");
        assertThat(names).doesNotContain("ShadingRule");
        assertThat(names.indexOf("FungalDamageRule")).isLessThan(names.indexOf("FoliarPestRule"));
        assertThat(orchestrator.getRules()).filteredOn(r -> r.name().equals("FungalDamageRule") || r.name().equals("FoliarPestRule"))
                .allSatisfy(r -> {
                    assertThat(r.branch()).isEqualTo(RuleBranch.INSUMO);
                    assertThat(r.usaDiagnostico()).isTrue();
                });
    }

    @Test
    @DisplayName("F-F2 y F-04 corren en el motor de zonas antes que el plan de nutrición: sanidad antes que rutina")
    void zoneRulesRunBeforeTheNutritionPlan() {
        assertThat(zoneEngine.getRules()).extracting(ZoneRule::name)
                .containsSubsequence("FungalConditionRule", "OutbreakRule", "LowEcRule", "NutritionPlanRule");
    }

    @Test
    @DisplayName("las cuatro reglas y las precondiciones aparecen entre las piezas que el simulador controla, con sus llaves")
    void everyRuleIsDemoControllable() {
        assertThat(registry.find("FungalDamageRule")).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).containsExactly("persistence"));
        assertThat(registry.find("FoliarPestRule")).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).containsExactly("persistence"));
        assertThat(registry.find("FungalConditionRule")).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).containsExactly("readings"));
        assertThat(registry.find("OutbreakRule")).hasValueSatisfying(c ->
                assertThat(c.cooldownKeys()).containsExactly("window"));
        assertThat(registry.find(ApplicationPreconditions.NAME)).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).contains("cap"));
    }

    @Test
    @DisplayName("ninguna otra pieza de decisión comparte la prioridad de una regla biótica")
    void bioticPrioritiesAreUnique() {
        List<String> bioticRules = List.of("FungalDamageRule", "FoliarPestRule", "FungalConditionRule", "OutbreakRule");
        for (DemoControllable rule : controllables) {
            if (!bioticRules.contains(rule.name())) {
                continue;
            }
            assertThat(controllables.stream().filter(o -> o != rule && o.priority() == rule.priority()))
                    .as("prioridad de " + rule.name()).isEmpty();
        }
    }
}
