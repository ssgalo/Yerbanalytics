package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.solarstress.SolarStressTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("F-S3 · Estrés solar sin radiación alta")
class SolarStressUnexplainedRuleTest {

    private SolarExposure exposure;
    private SolarStressUnexplainedRule rule;

    @BeforeEach
    void setUp() {
        exposure = mock(SolarExposure.class);
        rule = new SolarStressUnexplainedRule(exposure);
    }

    private void peaks(Double light, Double temp) {
        when(exposure.peaks(any(RuleContext.class), anyBoolean())).thenReturn(new SolarExposure.Peaks(light, temp));
    }

    private List<RuleAction> evaluate(RuleContext ctx) {
        return rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private static RuleContext stressed() {
        Instant now = at("09:00");
        return ctx(sector("Estrés solar", "Media", 88.0, now), now, metric("humSus", 50.0));
    }

    @Test
    void sinRadiacionAltaSoloAvisaYNoActua() {
        peaks(55.0, 27.0);

        List<RuleAction> actions = evaluate(stressed());

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.ALERTA);
        DetalleAlerta alert = (DetalleAlerta) actions.get(0).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("Síntoma no explicado por radiación").contains("Inspeccionar").contains("MZ-1-045");
    }

    @Test
    void laZonaGrisNoEsDeNinguna() {
        // Luz máxima 70 % y 31 °C: ni F-S1 (≥ 80 / ≥ 32) ni F-S3 (< 60 / < 30). Es lo que dice la v2.
        peaks(70.0, 31.0);

        assertThat(evaluate(stressed())).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void conLaLuzBajaPeroElCalorEnLaZonaGrisNoAvisa() {
        peaks(50.0, 31.0);

        assertThat(has(evaluate(stressed()), ActionType.ALERTA)).isFalse();
    }

    @Test
    void conLaTemperaturaBajaPeroLaLuzEnLaZonaGrisNoAvisa() {
        peaks(70.0, 25.0);

        assertThat(has(evaluate(stressed()), ActionType.ALERTA)).isFalse();
    }

    @Test
    void enElBordeDeLasDosCondicionesNoAvisa() {
        peaks(60.0, 30.0);

        assertThat(has(evaluate(stressed()), ActionType.ALERTA)).as("< 60 y < 30, estrictos").isFalse();
    }

    @Test
    void conRadiacionAltaNoLeCorrespondeYLoCubreF_S1() {
        peaks(88.0, 30.0);

        assertThat(has(evaluate(stressed()), ActionType.ALERTA)).isFalse();
    }

    @Test
    void sinLecturasNoSabeSiHuboRadiacionYNoAvisa() {
        peaks(null, null);

        assertThat(evaluate(stressed())).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void elBypassDeMuestrasLeEsPedidoAlLectorDeExposicion() {
        peaks(40.0, 24.0);
        Instant now = at("09:00");
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, ContextoRiego.vacio(),
                bypass("SolarStressUnexplainedRule", "samples"), metric("humSus", 50.0));

        evaluate(ctx);

        verify(exposure).peaks(any(RuleContext.class), eq(true));
    }

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        peaks(40.0, 24.0);
        Instant now = at("09:00");

        List<RuleAction> actions = evaluate(ctx(sector("Clorosis", "Alta", 90.0, now), now, metric("humSus", 50.0)));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void esUnaReglaDeAvisoSinAccionConSuBypassDeMuestras() {
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.bypassKeys()).containsExactly("samples");
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.name()).isEqualTo("SolarStressUnexplainedRule");
        assertThat(rule.code()).isEqualTo("F-S3");
        assertThat(rule.branch()).isEqualTo(RuleBranch.SEGUIMIENTO);
        assertThat(rule.parametros()).extracting(DefinicionParametro::clave).containsExactlyInAnyOrder(
                "diagnostico.confianza-minima", "diagnostico.vigencia-horas", "estres.luz-baja", "estres.temp-baja");
    }
}
