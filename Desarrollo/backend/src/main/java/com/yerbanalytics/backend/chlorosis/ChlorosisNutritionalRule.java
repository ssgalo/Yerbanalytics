package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleInsumo;
import com.yerbanalytics.backend.engine.DiagnosisGate;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.hardening.HardeningPhase;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * F-C2 · Clorosis nutricional (reglas_v2 §8.5): con un diagnóstico Clorosis vigente y <b>sin</b> ninguna causa de F-C1,
 * pide fertilizante para el sector y avisa con un {@code WARNING} ({@code CRITICAL} con severidad alta). Durante la
 * rustificación sólo cuenta la severidad moderada o alta: un amarillamiento leve es esperable con el cambio de luz.
 *
 * <p><b>«Sin ninguna causa de F-C1» lo garantiza la cadena, no esta regla</b>: F-C1 corre antes y, si encontró una causa,
 * corta la rama de insumos del sector y esta regla queda sin alcanzar. Así la cuenta de las causas vive en un solo lugar.
 *
 * <p><b>Repetir.</b> Mientras el diagnóstico siga llegando, la regla vuelve a pedir; el intervalo mínimo entre fertilizaciones
 * del sector (5 días) lo respeta el {@code ApplicationService} (P-D2), que difiere el pedido hasta cumplirlo. Por eso no tiene
 * un cooldown propio: el que acorta el simulador es {@code interval-fertilizer} de {@code ApplicationPreconditions}.
 */
@Component
public class ChlorosisNutritionalRule implements Rule {

    /** Prioridad: justo después de F-C1, que le corta la rama; única entre las reglas por sector y por macro-zona. */
    private static final int PRIORITY = 25;
    public static final String NAME = "ChlorosisNutritionalRule";

    private final HardeningPhase hardening;

    public ChlorosisNutritionalRule(HardeningPhase hardening) {
        this.hardening = hardening;
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
        return "🌿 Clorosis nutricional (F-C2)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    /** Decide con el diagnóstico de IA: actúa una vez por diagnóstico nuevo. */
    @Override
    public boolean usaDiagnostico() {
        return true;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return DiagnosisGate.PARAMETERS;
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Optional<RuleAction> notApplicable = DiagnosisGate.check(ctx, ev, NAME, "clorosis", "Clorosis"::equals);
        if (notApplicable.isPresent()) {
            return List.of(notApplicable.get());
        }
        String sectorId = ctx.sector().getId();
        Severity severity = Severity.parse(ctx.sector().getDiagnosisSev()).orElse(Severity.MILD);

        if (hardening.isUnderway(ctx.zona(), ctx.now())
                && !ev.compararFijo("Severidad moderada o alta (durante la rustificación)",
                severity.isModerateOrHigher(), Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "Clorosis leve en " + sectorId
                    + " durante la rustificación: un amarillamiento leve es esperable con el cambio de luz, no se fertiliza."));
        }

        String order = "Clorosis nutricional en " + sectorId + ": sin una causa que el fertilizante no corrija, se pide fertilizante.";
        String alert = "Clorosis nutricional en " + sectorId;
        NivelAlerta level = severity.isHigh() ? NivelAlerta.CRITICAL : NivelAlerta.WARNING;
        return List.of(
                RuleAction.of(ActionType.ACTIVAR_BOMBA, NAME, order, new DetalleInsumo(Supply.FERTILIZER, true)),
                new RuleAction(ActionType.ALERTA, NAME, alert, new DetalleAlerta(level, alert)));
    }
}
