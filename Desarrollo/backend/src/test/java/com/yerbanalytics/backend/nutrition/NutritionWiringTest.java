package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque real: el plan de nutrición queda conectado al motor, al control de demo y a la ingesta. Sólo lectura: no ensucia
 * la base.
 */
@SpringBootTest
@DisplayName("Plan de nutrición · conexión en el contexto real")
class NutritionWiringTest {

    @Autowired
    private ZoneRuleEngine engine;

    @Autowired
    private DemoControllableRegistry registry;

    @Autowired
    private List<DemoControllable> controllables;

    @Test
    @DisplayName("el motor de zonas corre N-02 antes que N-01: la CE baja adelanta el plan en el mismo pulso")
    void engineRunsLowEcBeforeThePlan() {
        // Las de la mediasombra, también de zona, van antes y no cambian este orden.
        assertThat(engine.getRules()).extracting(ZoneRule::name).containsSubsequence("LowEcRule", "NutritionPlanRule");
    }

    @Test
    @DisplayName("las dos reglas aparecen entre las piezas que el simulador puede controlar, con sus llaves")
    void bothRulesAreDemoControllable() {
        assertThat(registry.find("NutritionPlanRule")).hasValueSatisfying(c -> {
            assertThat(c.cooldownKeys()).containsExactly("interval");
            assertThat(c.bypassKeys()).isEmpty();
        });
        assertThat(registry.find("LowEcRule")).hasValueSatisfying(c -> {
            assertThat(c.cooldownKeys()).containsExactly("no-fertilized");
            assertThat(c.bypassKeys()).containsExactly("samples");
        });
    }

    @Test
    @DisplayName("ninguna otra pieza de decisión comparte la prioridad de una regla de zona")
    void zoneRulePrioritiesAreUnique() {
        for (ZoneRule rule : engine.getRules()) {
            assertThat(controllables.stream().filter(o -> o != rule && o.priority() == rule.priority()))
                    .as("prioridad de " + rule.name()).isEmpty();
        }
    }
}
