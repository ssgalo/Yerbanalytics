package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.hardening.ZonePlanHardeningPhase;
import com.yerbanalytics.backend.hardening.HardeningPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque real: las reglas de clorosis y estrés solar quedan conectadas al motor por sector y al control de demo. Sólo
 * lectura: no ensucia la base.
 */
@SpringBootTest
@DisplayName("Clorosis y estrés solar · conexión en el contexto real")
class ChlorosisSolarWiringTest {

    private static final List<String> RULES = List.of("ChlorosisNonNutritionalRule", "ChlorosisNutritionalRule",
            "SolarStressRule", "SolarStressUnexplainedRule");

    @Autowired
    private RuleOrchestrator orchestrator;

    @Autowired
    private DemoControllableRegistry registry;

    @Autowired
    private List<DemoControllable> controllables;

    @Autowired
    private HardeningPhase hardening;

    @Test
    @DisplayName("las cuatro reglas corren en la cadena por sector; F-C1 va antes que F-C2 y F-S1 después de R-01")
    void rulesAreInTheChainInOrder() {
        List<String> names = orchestrator.getRules().stream().map(Rule::name).toList();

        assertThat(names).containsAll(RULES);
        assertThat(names.indexOf("ChlorosisNonNutritionalRule")).isLessThan(names.indexOf("ChlorosisNutritionalRule"));
        assertThat(names.indexOf("RiegoPorDeficitRule")).isLessThan(names.indexOf("SolarStressRule"));
        assertThat(orchestrator.getRules()).filteredOn(r -> RULES.contains(r.name())).allSatisfy(r ->
                assertThat(r.usaDiagnostico()).as(r.name()).isTrue());
    }

    @Test
    @DisplayName("F-C1 y F-C2 son de insumos, F-S1 de riego y F-S3 un aviso de seguimiento")
    void branches() {
        assertThat(branch("ChlorosisNonNutritionalRule")).isEqualTo(RuleBranch.INSUMO);
        assertThat(branch("ChlorosisNutritionalRule")).isEqualTo(RuleBranch.INSUMO);
        assertThat(branch("SolarStressRule")).isEqualTo(RuleBranch.RIEGO);
        assertThat(branch("SolarStressUnexplainedRule")).isEqualTo(RuleBranch.SEGUIMIENTO);
    }

    @Test
    @DisplayName("aparecen entre las piezas que el simulador puede controlar, con sus llaves")
    void everyRuleIsDemoControllable() {
        assertThat(registry.find("SolarStressRule")).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).containsExactlyInAnyOrder("window", "samples"));
        assertThat(registry.find("SolarStressUnexplainedRule")).hasValueSatisfying(c ->
                assertThat(c.bypassKeys()).containsExactly("samples"));
        assertThat(registry.find("ChlorosisNonNutritionalRule")).isPresent();
        assertThat(registry.find("ChlorosisNutritionalRule")).isPresent();
    }

    @Test
    @DisplayName("ninguna otra pieza de decisión comparte la prioridad de estas reglas")
    void prioritiesAreUnique() {
        for (DemoControllable rule : controllables) {
            if (RULES.contains(rule.name())) {
                assertThat(controllables.stream().filter(o -> o != rule && o.priority() == rule.priority()))
                        .as("prioridad de " + rule.name()).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("la rustificación se resuelve hoy con el plan global")
    void hardeningComesFromTheGlobalPlan() {
        assertThat(hardening).isInstanceOf(ZonePlanHardeningPhase.class);
    }

    private RuleBranch branch(String name) {
        return orchestrator.getRules().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow().branch();
    }
}
