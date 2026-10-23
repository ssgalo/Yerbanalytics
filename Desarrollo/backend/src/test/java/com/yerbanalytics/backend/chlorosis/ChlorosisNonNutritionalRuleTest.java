package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelPendingApplication;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import com.yerbanalytics.backend.readings.ReadingWindow;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yerbanalytics.backend.chlorosis.ChlorosisTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("F-C1 · Clorosis con causa no nutricional")
class ChlorosisNonNutritionalRuleTest {

    private ReadingHistory readings;
    private ChlorosisNonNutritionalRule rule;

    @BeforeEach
    void setUp() {
        readings = mock(ReadingHistory.class);
        rule = new ChlorosisNonNutritionalRule(readings);
        averageMoisture(60.0);
    }

    private void averageMoisture(Double value) {
        when(readings.average(eq("MZ-1"), eq(ReadingMetric.HUM_SUS), any(ReadingWindow.class)))
                .thenReturn(Optional.ofNullable(value));
    }

    private List<RuleAction> evaluate(RuleContext ctx) {
        return rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private static RuleContext chlorosis(Double ce, Double ph) {
        return ctx(sector("Clorosis", "Media", 88.0), metric("ce", ce), metric("phSuelo", ph));
    }

    private static DetalleAlerta alert(List<RuleAction> actions) {
        return (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
    }

    @Test
    void asfixiaRadicularCortaLosInsumosYAvisaConLaCausa() {
        averageMoisture(84.0);

        List<RuleAction> actions = evaluate(chlorosis(0.8, 5.5));

        RuleAction abort = first(actions, ActionType.ABORT_INSUMO);
        assertThat(abort.detalle()).isEqualTo(new CancelPendingApplication(Supply.FERTILIZER, "ChlorosisNutritionalRule"));
        assertThat(alert(actions).nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert(actions).texto()).contains("asfixia radicular").contains("MZ-1-045");
    }

    @Test
    void salinidadConCe2_3() {
        List<RuleAction> actions = evaluate(chlorosis(2.3, 5.5));

        assertThat(has(actions, ActionType.ABORT_INSUMO)).isTrue();
        assertThat(alert(actions).texto()).contains("salinidad");
    }

    @Test
    void phAltoEsClorosisFerrica() {
        List<RuleAction> actions = evaluate(chlorosis(0.8, 6.8));

        assertThat(has(actions, ActionType.ABORT_INSUMO)).isTrue();
        assertThat(alert(actions).texto()).contains("clorosis férrica");
    }

    @Test
    void enElBordeLaCausaSeCumple() {
        assertThat(has(evaluate(chlorosis(2.0, 5.5)), ActionType.ABORT_INSUMO)).as("CE = 2,0").isTrue();
        assertThat(has(evaluate(chlorosis(0.8, 6.5)), ActionType.ABORT_INSUMO)).as("pH = 6,5").isTrue();
    }

    @Test
    void justoPorDebajoDelBordeNoHayCausa() {
        assertThat(has(evaluate(chlorosis(1.9, 6.4)), ActionType.ABORT_INSUMO)).isFalse();
    }

    @Test
    void laHumedadMediaIgualAlUmbralNoEsAsfixia() {
        averageMoisture(80.0);

        assertThat(has(evaluate(chlorosis(0.8, 5.5)), ActionType.ABORT_INSUMO)).as("> 80, no ≥").isFalse();
    }

    @Test
    void conVariasCausasLasDiceTodas() {
        averageMoisture(85.0);

        String text = alert(evaluate(chlorosis(2.4, 6.9))).texto();

        assertThat(text).contains("asfixia radicular").contains("salinidad").contains("clorosis férrica");
    }

    @Test
    void sinCausaDejaPasarAFC2() {
        List<RuleAction> actions = evaluate(chlorosis(0.8, 5.5));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("F-C2");
    }

    @Test
    void sinDatoNoHayCausa() {
        averageMoisture(null);

        assertThat(has(evaluate(chlorosis(null, null)), ActionType.ABORT_INSUMO)).isFalse();
    }

    @Test
    void unaLecturaDescartadaPorS03NoCuentaComoCausa() {
        RuleContext ctx = ctx(sector("Clorosis", "Media", 88.0), com.yerbanalytics.backend.engine.overrides.RuleOverrides.NONE,
                Set.of("ce"), metric("ce", 2.5), metric("phSuelo", 5.5));

        assertThat(has(evaluate(ctx), ActionType.ABORT_INSUMO)).isFalse();
    }

    @Test
    void laHumedadSePromediaSobreLasHorasDelCatalogo() {
        averageMoisture(84.0);

        evaluate(chlorosis(0.8, 5.5));

        ArgumentCaptor<ReadingWindow> window = ArgumentCaptor.forClass(ReadingWindow.class);
        verify(readings).average(eq("MZ-1"), eq(ReadingMetric.HUM_SUS), window.capture());
        assertThat(window.getValue().from()).isEqualTo(NOW.minus(Duration.ofHours(48)));
    }

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        List<RuleAction> actions = evaluate(ctx(sector("Estrés solar", "Alta", 90.0), metric("ce", 2.5)));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        verifyNoInteractions(readings);
    }

    @Test
    void unDiagnosticoSinConfianzaNoAlcanza() {
        averageMoisture(90.0);

        List<RuleAction> actions = evaluate(ctx(sector("Clorosis", "Alta", 40.0), metric("ce", 2.5)));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void esUnaReglaDeInsumosDelDiagnosticoSinLlavesDeDemoPropias() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.bypassKeys()).isEmpty();
        assertThat(rule.name()).isEqualTo("ChlorosisNonNutritionalRule");
        assertThat(rule.code()).isEqualTo("F-C1");
        assertThat(rule.parametros()).extracting(DefinicionParametro::clave).containsExactlyInAnyOrder(
                "diagnostico.confianza-minima", "diagnostico.vigencia-horas", "clorosis.humedad-asfixia",
                "clorosis.humedad-horas", "clorosis.ce-salinidad", "clorosis.ph-ferrica");
    }

    @Test
    void correAntesQueFC2PorqueLeCortaLaRama() {
        assertThat(rule.priority()).isLessThan(new ChlorosisNutritionalRule(
                (zone, now) -> false).priority());
    }
}
