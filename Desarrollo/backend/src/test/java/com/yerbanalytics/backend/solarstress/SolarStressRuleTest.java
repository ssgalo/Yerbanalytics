package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.solarstress.SolarStressTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("F-S1 · Estrés solar con radiación alta")
class SolarStressRuleTest {

    private SolarExposure exposure;
    private SolarStressRule rule;

    @BeforeEach
    void setUp() {
        exposure = mock(SolarExposure.class);
        rule = new SolarStressRule(exposure);
        peaks(88.0, 30.0);
    }

    private void peaks(Double light, Double temp) {
        when(exposure.peaks(any(RuleContext.class), anyBoolean())).thenReturn(new SolarExposure.Peaks(light, temp));
    }

    private List<RuleAction> evaluate(RuleContext ctx) {
        return rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private static RuleContext stressed(String localTime, Double substrate, String severity) {
        Instant now = at(localTime);
        return ctx(sector("Estrés solar", severity, 88.0, now), now, metric("humSus", substrate));
    }

    @Test
    void conRadiacionAltaYSustratoBajoRiegaElSectorYAvisa() {
        // La luz llegó a 88 % ayer a las 13:00 y el sustrato está en 50 %, a las 09:00.
        List<RuleAction> actions = evaluate(stressed("09:00", 50.0, "Media"));

        RuleAction irrigation = first(actions, ActionType.ACTIVAR_VALVULA);
        assertThat(irrigation.ruleName()).isEqualTo("SolarStressRule");
        DetalleRiego detail = (DetalleRiego) irrigation.detalle();
        assertThat(detail.duracionSeg()).isGreaterThan(0);
        assertThat(detail.humedad()).isEqualTo(50.0);
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.texto()).contains("MZ-1-045");
    }

    @Test
    void elVolumenSaleDeLaFormulaDeRiegoDeLaHumedadObjetivo() {
        // (65 − 50) × 0,2 = 3 L a 30 L/h: 360 s.
        DetalleRiego detail = (DetalleRiego) first(evaluate(stressed("09:00", 50.0, "Media")), ActionType.ACTIVAR_VALVULA).detalle();

        assertThat(detail.duracionSeg()).isEqualTo(360);
    }

    @Test
    void conSeveridadAltaLaAlertaEsCritical() {
        List<RuleAction> actions = evaluate(stressed("09:00", 50.0, "Alta"));

        assertThat(((DetalleAlerta) first(actions, ActionType.ALERTA).detalle()).nivel()).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void elCalorAlcanzaAunqueLaLuzNoLlegue() {
        peaks(40.0, 33.0);

        assertThat(has(evaluate(stressed("09:00", 50.0, "Media")), ActionType.ACTIVAR_VALVULA)).isTrue();
    }

    @Test
    void sinLuzNiCalorAltosNoRiega() {
        peaks(70.0, 31.0);

        List<RuleAction> actions = evaluate(stressed("09:00", 50.0, "Media"));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void enElBordeDeLaLuzYDeLaTemperaturaCuenta() {
        peaks(80.0, 20.0);
        assertThat(has(evaluate(stressed("09:00", 50.0, "Media")), ActionType.ACTIVAR_VALVULA)).as("luz = 80").isTrue();
        peaks(20.0, 32.0);
        assertThat(has(evaluate(stressed("09:00", 50.0, "Media")), ActionType.ACTIVAR_VALVULA)).as("temp = 32").isTrue();
    }

    @Test
    void conSustratoSuficienteNoRiega() {
        List<RuleAction> actions = evaluate(stressed("09:00", 58.0, "Media"));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void conElSustratoEnElUmbralTampocoRiega() {
        assertThat(has(evaluate(stressed("09:00", 55.0, "Media")), ActionType.ACTIVAR_VALVULA)).isFalse();
    }

    @Test
    void sinLecturaDeHumedadNoRiega() {
        assertThat(has(evaluate(stressed("09:00", null, "Media")), ActionType.ACTIVAR_VALVULA)).isFalse();
    }

    @Test
    void fueraDeLaVentanaDeRiegoNoRiega() {
        List<RuleAction> actions = evaluate(stressed("20:00", 50.0, "Media"));

        assertThat(has(actions, ActionType.ACTIVAR_VALVULA)).isFalse();
        assertThat(actions.get(0).motivo()).contains("ventana");
    }

    @Test
    void conElBypassDeVentanaRiegaDeNoche() {
        Instant now = at("20:00");
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, ContextoRiego.vacio(),
                bypass("SolarStressRule", "window"), metric("humSus", 50.0));

        assertThat(has(evaluate(ctx), ActionType.ACTIVAR_VALVULA)).isTrue();
    }

    @Test
    void conUnaAplicacionRecienteRespetaLaPausaDeR06() {
        Instant now = at("09:00");
        ContextoRiego riego = new ContextoRiego(null, null, null, now.minusSeconds(3600).toEpochMilli(), null, null);
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, riego, RuleOverrides.NONE, metric("humSus", 50.0));

        List<RuleAction> actions = evaluate(ctx);

        assertThat(has(actions, ActionType.ACTIVAR_VALVULA)).isFalse();
        assertThat(actions.get(0).motivo()).contains("R-06");
    }

    @Test
    void conUnaAplicacionViejaLaPausaYaSeCumplio() {
        Instant now = at("09:00");
        ContextoRiego riego = new ContextoRiego(null, null, null, now.minusSeconds(8 * 3600).toEpochMilli(), null, null);
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, riego, RuleOverrides.NONE, metric("humSus", 50.0));

        assertThat(has(evaluate(ctx), ActionType.ACTIVAR_VALVULA)).isTrue();
    }

    @Test
    void laPausaTomaElCooldownDeR06DelSimulador() {
        Instant now = at("09:00");
        ContextoRiego riego = new ContextoRiego(null, null, null, now.minusSeconds(3600).toEpochMilli(), null, null);
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, riego,
                cooldown("PausaTrasAplicacionRule", "pause", 0), metric("humSus", 50.0));

        assertThat(has(evaluate(ctx), ActionType.ACTIVAR_VALVULA)).isTrue();
    }

    @Test
    void elBypassDeMuestrasLeEsPedidoAlLectorDeExposicion() {
        Instant now = at("09:00");
        RuleContext ctx = ctx(sector("Estrés solar", "Media", 88.0, now), now, ContextoRiego.vacio(),
                bypass("SolarStressRule", "samples"), metric("humSus", 50.0));

        evaluate(ctx);

        verify(exposure).peaks(ctx, true);
    }

    @Test
    void otroDiagnosticoNoEsDeEstaRegla() {
        Instant now = at("09:00");
        SectorEntity other = sector("Clorosis", "Alta", 90.0, now);

        List<RuleAction> actions = evaluate(ctx(other, now, metric("humSus", 50.0)));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void unDiagnosticoSinConfianzaNoAlcanza() {
        Instant now = at("09:00");

        List<RuleAction> actions = evaluate(ctx(sector("Estrés solar", "Alta", 40.0, now), now, metric("humSus", 50.0)));

        assertThat(has(actions, ActionType.ACTIVAR_VALVULA)).isFalse();
    }

    @Test
    void esUnaReglaDeRiegoDelDiagnosticoConSusLlavesDeDemo() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.usaDiagnostico()).isTrue();
        assertThat(rule.bypassKeys()).containsExactlyInAnyOrder("window", "samples");
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.name()).isEqualTo("SolarStressRule");
        assertThat(rule.code()).isEqualTo("F-S1");
        assertThat(rule.parametros()).extracting(DefinicionParametro::clave).contains(
                "diagnostico.confianza-minima", "estres.luz-alta", "estres.temp-alta", "estres.humedad-reposicion",
                "riego.ventana-normal", "riego.humedad-objetivo", "riego.litros-por-punto", "riego.volumen-max-evento",
                "riego.caudal-emisor", "riego.pausa-tras-aplicacion");
    }

    @Test
    void correDespuesDeR01YEnLaRamaDeRiegoParaQueLosCortesDeSeguridadLeAlcancen() {
        assertThat(rule.priority()).isGreaterThan(11);
    }
}
