package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.biotic.BioticTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("F-F2 · Condición predisponente al hongo")
class FungalConditionRuleTest {

    private ReadingHistory readings;
    private FungalConditionRule rule;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        readings = mock(ReadingHistory.class);
        rule = new FungalConditionRule(readings);
        zone = zone("MZ-1");
    }

    /** Las últimas lecturas de pulso de la zona, la más vieja primero. */
    private void latestReadings(ZoneReadingEntity... chronological) {
        when(readings.latest(eq("MZ-1"), anyInt(), anyBoolean())).thenReturn(List.of(chronological));
    }

    private ZoneRuleContext ctx(RuleOverrides overrides) {
        List<SectorEntity> sectors = List.of(healthy(zone, 1), healthy(zone, 2));
        return new ZoneRuleContext(zone, sectors, NOW, Map.of(), Map.of(), false, Set.of(), overrides);
    }

    private List<RuleAction> evaluate() {
        return rule.evaluate(ctx(RuleOverrides.NONE), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    @Test
    void cuandoSeCumplenLasTresAvisaConUnWarningYNoAplicaNada() {
        // DPV 0,2 y 0,25, 24 °C y sustrato 78 %; la lectura previa no cumplía (DPV 0,8): el evento empieza ahora.
        latestReadings(reading(9, 78.0, 0.8, 24.0), reading(5, 78.0, 0.2, 24.0), reading(1, 78.0, 0.25, 24.0));

        List<RuleAction> actions = evaluate();

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.ALERTA);
        DetalleAlerta alert = (DetalleAlerta) actions.get(0).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("Condiciones favorables a enfermedades fúngicas").contains("MZ-1");
    }

    @Test
    void conFrioNoActua() {
        latestReadings(reading(9, 78.0, 0.8, 15.0), reading(5, 78.0, 0.2, 15.0), reading(1, 78.0, 0.25, 15.0));

        assertThat(evaluate()).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void conMuchoCalorNoActua() {
        latestReadings(reading(9, 78.0, 0.8, 30.0), reading(5, 78.0, 0.2, 30.0), reading(1, 78.0, 0.25, 30.0));

        assertThat(has(evaluate(), ActionType.ALERTA)).isFalse();
    }

    @Test
    void conElSustratoSecoNoActua() {
        latestReadings(reading(9, 70.0, 0.8, 24.0), reading(5, 70.0, 0.2, 24.0), reading(1, 70.0, 0.25, 24.0));

        assertThat(has(evaluate(), ActionType.ALERTA)).isFalse();
    }

    @Test
    void conElAireSecoNoActua() {
        latestReadings(reading(9, 78.0, 0.8, 24.0), reading(5, 78.0, 0.2, 24.0), reading(1, 78.0, 0.9, 24.0));

        assertThat(has(evaluate(), ActionType.ALERTA)).isFalse();
    }

    @Test
    void unaSolaLecturaNoAlcanza() {
        latestReadings(reading(1, 78.0, 0.2, 24.0));

        assertThat(evaluate()).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void siLaCondicionYaEstabaEnLaLecturaAnteriorNoRepiteElAviso() {
        // Las tres lecturas cumplen: el evento empezó antes, ya se avisó.
        latestReadings(reading(9, 78.0, 0.2, 24.0), reading(5, 78.0, 0.2, 24.0), reading(1, 78.0, 0.25, 24.0));

        List<RuleAction> actions = evaluate();

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("ya estaba");
    }

    @Test
    void conSoloDosLecturasElEventoEmpiezaAhora() {
        latestReadings(reading(5, 78.0, 0.2, 24.0), reading(1, 78.0, 0.25, 24.0));

        assertThat(has(evaluate(), ActionType.ALERTA)).isTrue();
    }

    @Test
    void conElBypassDeLecturasBastaUnaSolaConDpvBajo() {
        latestReadings(reading(5, 78.0, 0.9, 24.0), reading(1, 78.0, 0.2, 24.0));

        List<RuleAction> actions = rule.evaluate(ctx(bypass(FungalConditionRule.NAME, "readings")),
                new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.ALERTA)).isTrue();
    }

    @Test
    void sinLecturasNoDecide() {
        latestReadings();

        assertThat(evaluate()).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void funcionaSinDiagnosticoNiSectoresAfectados() {
        // Es de la macro-zona: no mira ningún diagnóstico.
        latestReadings(reading(5, 78.0, 0.2, 24.0), reading(1, 78.0, 0.25, 24.0));

        assertThat(has(evaluate(), ActionType.ALERTA)).isTrue();
    }

    @Test
    void esUnaReglaDeMacroZonaDeLaRamaDeInsumos() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.bypassKeys()).containsExactly("readings");
        assertThat(rule.name()).isEqualTo("FungalConditionRule");
        assertThat(rule.code()).isEqualTo("F-F2");
        assertThat(rule.parametros()).extracting(p -> p.clave()).containsExactlyInAnyOrder(
                "biotico.dpv-saturado", "biotico.temp-predisponente-min", "biotico.temp-predisponente-max",
                "biotico.humedad-predisponente");
    }
}
