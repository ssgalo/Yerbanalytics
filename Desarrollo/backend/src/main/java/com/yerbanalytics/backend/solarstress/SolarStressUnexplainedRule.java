package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DiagnosisGate;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.parametros.ParametrosEstres;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F-S3 · Estrés solar sin radiación alta (reglas_v2 §8.5): ante un diagnóstico Estrés solar vigente, si en las últimas 48 h
 * la luz fue {@code <} {@code estres.luz-baja} (60 %) <b>y</b> la temperatura {@code <} {@code estres.temp-baja} (30 °C), no
 * hay nada que hacer con la malla ni con el agua (si falta agua, ya riega R-01) y sólo se avisa con un {@code WARNING}
 * «Síntoma no explicado por radiación: posible estrés hídrico o problema de raíz. Inspeccionar».
 *
 * <p><b>La zona gris es a propósito.</b> Entre la luz baja y la alta (60–80 %) y entre la temperatura baja y la alta
 * (30–32 °C) ninguna de las dos reglas actúa: es lo que dice la v2, y hay un test que lo fija para que no se «arregle» sin
 * querer. La luz se mide como en F-S1 (pico entre las 10 y las 16 h).
 *
 * <p>Está en la rama de seguimiento y no en la de riego para que su aviso no dependa de los cortes de esa rama (ningún corte
 * alcanza a la de seguimiento): no ordena nada. Tampoco en la global, que el grafo de reglas dibuja antes de todas las demás.
 *
 * <p><b>Simulador:</b> el bypass {@value #BYPASS_SAMPLES} usa la última lectura de luz y temperatura en lugar de las 48 h.
 */
@Component
public class SolarStressUnexplainedRule implements Rule {

    /** Prioridad: después de F-S1, con quien comparte el diagnóstico; única entre las reglas por sector y por macro-zona. */
    private static final int PRIORITY = 27;
    public static final String NAME = "SolarStressUnexplainedRule";
    /** Bypass key: uses the last light and temperature reading instead of the last 48 h. */
    public static final String BYPASS_SAMPLES = "samples";

    private final SolarExposure exposure;

    public SolarStressUnexplainedRule(SolarExposure exposure) {
        this.exposure = exposure;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "🔍 Estrés solar sin radiación alta (F-S3)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.SEGUIMIENTO;
    }

    /** Decide con el diagnóstico de IA: actúa una vez por diagnóstico nuevo. */
    @Override
    public boolean usaDiagnostico() {
        return true;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_SAMPLES);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS,
                ParametrosEstres.LOW_LIGHT, ParametrosEstres.LOW_TEMP);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Optional<RuleAction> notApplicable = DiagnosisGate.check(ctx, ev, NAME, "estrés solar", "Estrés solar"::equals);
        if (notApplicable.isPresent()) {
            return List.of(notApplicable.get());
        }
        String sectorId = ctx.sector().getId();

        SolarExposure.Peaks peaks = exposure.peaks(ctx, ctx.isBypassed(NAME, BYPASS_SAMPLES));
        boolean lowLight = ev.comparar("Luz máxima entre las 10 y las 16 h de las últimas 48 h", peaks.light(), Operador.LT,
                ParametrosEstres.LOW_LIGHT);
        boolean mildTemp = ev.comparar("Temperatura máxima de las últimas 48 h", peaks.temp(), Operador.LT,
                ParametrosEstres.LOW_TEMP);
        if (!(lowLight && mildTemp)) {
            return List.of(RuleAction.noopInfo(NAME, "El estrés solar de " + sectorId
                    + " se explica por la radiación o no hay datos suficientes de las últimas 48 h: no corresponde el aviso (F-S3)."));
        }

        String text = String.format("Síntoma no explicado por radiación en %s: posible estrés hídrico o problema de raíz. Inspeccionar.",
                sectorId);
        return List.of(new RuleAction(ActionType.ALERTA, NAME, text, new DetalleAlerta(NivelAlerta.WARNING, text)));
    }
}
