package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleInsumo;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.hardening.HardeningPhase;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yerbanalytics.backend.chlorosis.ChlorosisTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("F-C2 · Clorosis nutricional")
class ChlorosisNutritionalRuleTest {

    private HardeningPhase hardening;
    private ChlorosisNutritionalRule rule;

    @BeforeEach
    void setUp() {
        hardening = mock(HardeningPhase.class);
        rule = new ChlorosisNutritionalRule(hardening);
    }

    private void underHardening(boolean underway) {
        when(hardening.isUnderway(any(), any())).thenReturn(underway);
    }

    private List<RuleAction> evaluate(SectorEntity sector) {
        return rule.evaluate(ctx(sector), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    @Test
    void conClorosisVigentePideFertilizanteYAvisaConUnWarning() {
        underHardening(false);

        List<RuleAction> actions = evaluate(sector("Clorosis", "Media", 88.0));

        assertThat(first(actions, ActionType.ACTIVAR_BOMBA).detalle()).isEqualTo(new DetalleInsumo(Supply.FERTILIZER, true));
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("MZ-1-045");
    }

    @Test
    void conSeveridadAltaLaAlertaEsCritical() {
        underHardening(false);

        List<RuleAction> actions = evaluate(sector("Clorosis", "Alta", 90.0));

        assertThat(((DetalleAlerta) first(actions, ActionType.ALERTA).detalle()).nivel()).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void fueraDeLaRustificacionTambienCuentaLaSeveridadLeve() {
        underHardening(false);

        assertThat(has(evaluate(sector("Clorosis", "Baja", 88.0)), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void durantePlanLaRustificacionLaSeveridadLeveEsEsperableYNoSeAplica() {
        underHardening(true);

        List<RuleAction> actions = evaluate(sector("Clorosis", "Baja", 88.0));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("rustificación");
    }

    @Test
    void durantePlanLaRustificacionLaSeveridadModeradaOAltaSiCuenta() {
        underHardening(true);

        assertThat(has(evaluate(sector("Clorosis", "Media", 88.0)), ActionType.ACTIVAR_BOMBA)).isTrue();
        assertThat(has(evaluate(sector("Clorosis", "Alta", 88.0)), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        underHardening(false);

        List<RuleAction> actions = evaluate(sector("Daño fúngico", "Alta", 90.0));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void unDiagnosticoViejoONoConfiableNoAplica() {
        underHardening(false);

        assertThat(has(evaluate(sector("Clorosis", "Alta", 40.0)), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void esUnaReglaDeInsumosDelDiagnostico() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.name()).isEqualTo("ChlorosisNutritionalRule");
        assertThat(rule.label()).contains("F-C2");
        assertThat(rule.parametros()).extracting(DefinicionParametro::clave)
                .containsExactlyInAnyOrder("diagnostico.confianza-minima", "diagnostico.vigencia-horas");
    }
}
