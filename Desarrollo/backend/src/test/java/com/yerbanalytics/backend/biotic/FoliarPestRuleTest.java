package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleInsumo;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
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

import static com.yerbanalytics.backend.biotic.BioticTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("F-P1 · Plaga foliar")
class FoliarPestRuleTest {

    private ReadingHistory readings;
    private DiagnosisHistory diagnoses;
    private FoliarPestRule rule;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        readings = mock(ReadingHistory.class);
        diagnoses = mock(DiagnosisHistory.class);
        rule = new FoliarPestRule(readings, diagnoses);
        zone = zone("MZ-1");
    }

    private void maxDpv(Double value) {
        when(readings.max(eq("MZ-1"), eq(ReadingMetric.DPV), any(ReadingWindow.class))).thenReturn(Optional.ofNullable(value));
    }

    private void firstCaptureOnly(String estado) {
        when(diagnoses.recent(eq("MZ-1-045"), eq(NOW), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(capture(estado, 88.0, 0)));
    }

    private List<RuleAction> evaluate(SectorEntity sector) {
        return rule.evaluate(ctx(sector), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private SectorEntity pest(String estado, String severity) {
        return sector(zone, 45, estado, severity, 88.0, 2);
    }

    @Test
    void conSeveridadModeradaPideFitosanitarioYAvisa() {
        maxDpv(1.0);

        List<RuleAction> actions = evaluate(pest("Plaga foliar", "Media"));

        assertThat(first(actions, ActionType.ACTIVAR_BOMBA).detalle()).isEqualTo(new DetalleInsumo(Supply.FUNGICIDE, true));
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("plaga foliar").contains("MZ-1-045");
    }

    @Test
    void conSeveridadAltaLaAlertaEsCritical() {
        maxDpv(1.0);

        List<RuleAction> actions = evaluate(pest("Plaga foliar", "Alta"));

        assertThat(((DetalleAlerta) first(actions, ActionType.ALERTA).detalle()).nivel()).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void elAcaroEsUnaPlagaFoliar() {
        maxDpv(1.0);

        assertThat(has(evaluate(pest("Ácaro", "Media")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void leveConCalorSecoEnLas72HorasAplica() {
        // El DPV llegó a 2,3 kPa hace 30 h: el máximo de la ventana de 72 h es 2,3.
        maxDpv(2.3);
        firstCaptureOnly("Plaga foliar");

        assertThat(has(evaluate(pest("Plaga foliar", "Baja")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void laVentanaDelDpvEsLaDelCatalogo() {
        maxDpv(2.3);
        firstCaptureOnly("Plaga foliar");

        evaluate(pest("Plaga foliar", "Baja"));

        ArgumentCaptor<ReadingWindow> window = ArgumentCaptor.forClass(ReadingWindow.class);
        verify(readings).max(eq("MZ-1"), eq(ReadingMetric.DPV), window.capture());
        assertThat(window.getValue().from()).isEqualTo(NOW.minus(Duration.ofHours(72)));
    }

    @Test
    void leveSinNadaQueLoJustifiqueDejaUnInfoYNoAplica() {
        maxDpv(1.1);
        firstCaptureOnly("Plaga foliar");

        List<RuleAction> actions = evaluate(pest("Plaga foliar", "Baja"));

        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isFalse();
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(has(actions, ActionType.NOOP_INFO)).isTrue();
    }

    @Test
    void sinLecturasDeDpvNoHayCalorSecoQueJustifique() {
        maxDpv(null);
        firstCaptureOnly("Plaga foliar");

        assertThat(has(evaluate(pest("Plaga foliar", "Baja")), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void leveYTempladoPeroConTresCapturasSeguidasAplica() {
        maxDpv(1.1);
        when(diagnoses.recent(eq("MZ-1-045"), eq(NOW), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(
                capture("Ácaro", 88.0, 0), capture("Plaga foliar", 88.0, 1), capture("Ácaro", 88.0, 2)));

        // Ácaro y Plaga foliar son la misma plaga: la racha sigue.
        assertThat(has(evaluate(pest("Ácaro", "Baja")), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        List<RuleAction> actions = evaluate(pest("Daño fúngico", "Alta"));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void elDanoBioticoGenericoNoSeTrata() {
        List<RuleAction> actions = evaluate(pest("Daño biótico", "Alta"));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void esUnaReglaDeInsumosQueDecideConElDiagnostico() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.bypassKeys()).containsExactly("persistence");
        assertThat(rule.name()).isEqualTo("FoliarPestRule");
        assertThat(rule.code()).isEqualTo("F-P1");
    }
}
