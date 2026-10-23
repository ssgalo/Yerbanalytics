package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleInsumo;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.yerbanalytics.backend.biotic.BioticTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("F-F1 · Daño fúngico")
class FungalDamageRuleTest {

    private ReadingHistory readings;
    private DiagnosisHistory diagnoses;
    private FungalDamageRule rule;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        readings = mock(ReadingHistory.class);
        diagnoses = mock(DiagnosisHistory.class);
        rule = new FungalDamageRule(readings, diagnoses);
        zone = zone("MZ-1");
    }

    /** Las últimas lecturas de pulso de la zona, la más vieja primero. */
    private void latestReadings(ZoneReadingEntity... chronological) {
        when(readings.latest(eq("MZ-1"), anyInt(), anyBoolean())).thenReturn(List.of(chronological));
    }

    private void dryAir() {
        latestReadings(reading(5, 55.0, 0.9), reading(1, 55.0, 0.9));
    }

    private void captures(ConsecutiveCaptures.Capture... newestFirst) {
        when(diagnoses.recent(eq("MZ-1-045"), eq(NOW), anyInt())).thenReturn(List.of(newestFirst));
    }

    private void capturesOnConsecutiveDays(int days) {
        List<ConsecutiveCaptures.Capture> list = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            list.add(capture("Daño fúngico", 88.0, d));
        }
        when(diagnoses.recent(eq("MZ-1-045"), eq(NOW), anyInt())).thenReturn(list);
    }

    private List<RuleAction> evaluate(SectorEntity sector) {
        return rule.evaluate(ctx(sector), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private SectorEntity fungal(String severity) {
        return sector(zone, 45, "Daño fúngico", severity, 88.0, 2);
    }

    // --- Cuándo aplica ---

    @Test
    void conSeveridadModeradaPideFitosanitarioYAvisaConUnWarning() {
        dryAir();

        List<RuleAction> actions = evaluate(fungal("Media"));

        assertThat(first(actions, ActionType.ACTIVAR_BOMBA).detalle()).isEqualTo(new DetalleInsumo(Supply.FUNGICIDE, true));
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("Alta probabilidad de hongo").contains("MZ-1-045");
    }

    @Test
    void conSeveridadAltaLaAlertaEsCritical() {
        dryAir();

        List<RuleAction> actions = evaluate(fungal("Alta"));

        assertThat(((DetalleAlerta) first(actions, ActionType.ALERTA).detalle()).nivel()).isEqualTo(NivelAlerta.CRITICAL);
        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void leveConAmbienteSecoYPrimeraCapturaEspera() {
        dryAir();
        captures(capture("Daño fúngico", 88.0, 0));

        List<RuleAction> actions = evaluate(fungal("Baja"));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("próximas capturas");
    }

    @Test
    void leveConDpvDeAireSaturadoEnLasDosUltimasLecturasAplica() {
        latestReadings(reading(5, 60.0, 0.22), reading(1, 60.0, 0.25));
        captures(capture("Daño fúngico", 88.0, 0));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void leveConSustratoEncharcadoEnLasDosUltimasLecturasAplica() {
        latestReadings(reading(5, 82.0, 0.9), reading(1, 85.0, 0.9));
        captures(capture("Daño fúngico", 88.0, 0));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void unaSolaLecturaHumedaNoAlcanza() {
        // La anterior marcó 79 %: no son dos lecturas seguidas por encima del 80 %.
        latestReadings(reading(5, 79.0, 0.9), reading(1, 85.0, 0.9));
        captures(capture("Daño fúngico", 88.0, 0));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void conUnaSolaLecturaEnLas24HorasNoHayAmbienteQueJustifique() {
        latestReadings(reading(1, 85.0, 0.1));
        captures(capture("Daño fúngico", 88.0, 0));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void lasLecturasDeHaceMasDe24HorasNoCuentan() {
        latestReadings(reading(30, 85.0, 0.1), reading(26, 85.0, 0.1));
        captures(capture("Daño fúngico", 88.0, 0));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void leveYSecoPeroConTresCapturasSeguidasAplica() {
        dryAir();
        capturesOnConsecutiveDays(3);

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void conDosCapturasSeguidasTodaviaEspera() {
        dryAir();
        capturesOnConsecutiveDays(2);

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void conElBypassDePersistenciaBastaLaPrimeraCaptura() {
        dryAir();
        captures(capture("Daño fúngico", 88.0, 0));

        List<RuleAction> actions = rule.evaluate(ctx(fungal("Baja"), bypass(FungalDamageRule.NAME, "persistence")),
                new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    // --- Cuándo no corresponde ---

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        List<RuleAction> actions = evaluate(sector(zone, 45, "Plaga foliar", "Alta", 90.0, 2));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        verifyNoInteractions(readings, diagnoses);
    }

    @Test
    void unDiagnosticoSinConfianzaSuficienteNoAplica() {
        dryAir();

        List<RuleAction> actions = evaluate(sector(zone, 45, "Daño fúngico", "Alta", 60.0, 2));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).contains("confianza");
    }

    @Test
    void unDiagnosticoViejoNoAplica() {
        dryAir();

        assertThat(has(evaluate(sector(zone, 45, "Daño fúngico", "Alta", 90.0, 60)), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void unaCapturaDeBajaConfianzaCortaLaRacha() {
        dryAir();
        captures(capture("Daño fúngico", 88.0, 0), capture("Daño fúngico", 40.0, 1), capture("Daño fúngico", 88.0, 2));

        assertThat(has(evaluate(fungal("Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void conSeveridadModeradaNoConsultaElHistorial() {
        evaluate(fungal("Media"));

        verify(readings, never()).latest(eq("MZ-1"), anyInt(), anyBoolean());
        verifyNoInteractions(diagnoses);
    }

    // --- Contrato de regla ---

    @Test
    void esUnaReglaDeLaRamaDeInsumosQueDecideConElDiagnosticoYSeAcelera() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.bypassKeys()).containsExactly("persistence");
        assertThat(rule.name()).isEqualTo("FungalDamageRule");
        assertThat(rule.label()).contains("F-F1");
    }

    @Test
    void declaraLosParametrosQueLee() {
        assertThat(rule.parametros()).extracting(DefinicionParametro::clave).contains(
                "diagnostico.confianza-minima", "diagnostico.vigencia-horas", "biotico.humedad-hongo",
                "biotico.dpv-saturado", "biotico.capturas-seguidas");
    }
}
