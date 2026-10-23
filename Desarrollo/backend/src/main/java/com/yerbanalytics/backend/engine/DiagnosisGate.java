package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.supply.ValidDiagnosis;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * La compuerta que comparten las reglas que deciden con el diagnóstico de IA del sector (las bióticas F-F1/F-P1, la
 * clorosis F-C1/F-C2 y el estrés solar F-S1/F-S3): el diagnóstico es del estado que la regla trata y está vigente (P-D1:
 * reciente, concluyente y con la confianza mínima). Es una sola implementación para que ninguna regla repita la cuenta ni
 * la traza.
 *
 * <p>La regla que la usa tiene que declarar {@link #PARAMETERS} en su {@code parametros()}: la {@link Evaluacion} sólo deja
 * leer lo declarado.
 */
public final class DiagnosisGate {

    /** Los parámetros del catálogo que lee la compuerta: la confianza mínima y la vigencia (P-D1). */
    public static final List<DefinicionParametro> PARAMETERS =
            List.of(ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS);

    private DiagnosisGate() {
    }

    /**
     * @param ruleName   la regla que pregunta, para el Registro de Inacción
     * @param stateLabel cómo nombra el operador el problema que trata la regla («clorosis», «daño fúngico»)
     * @param treats     qué estados de diagnóstico trata la regla
     * @return vacío si la regla puede decidir; si no, el {@code NOOP_INFO} que explica por qué no corresponde
     */
    public static Optional<RuleAction> check(RuleContext ctx, Evaluacion ev, String ruleName, String stateLabel,
                                             Predicate<String> treats) {
        SectorEntity sector = ctx.sector();
        String state = sector.getDiagnosisEstado();
        if (state == null || !treats.test(state)) {
            return Optional.of(RuleAction.noopInfo(ruleName, String.format(
                    "El diagnóstico de %s es «%s»: no es %s.", sector.getId(), state, stateLabel)));
        }
        ValidDiagnosis.Verdict verdict = ValidDiagnosis.of(state, sector.getDiagnosisConf(), sector.getDiagnosisTs(),
                ctx.now(), ev.numero(ParametrosDiagnostico.CONFIANZA_MINIMA), ev.numero(ParametrosDiagnostico.VALIDITY_HOURS));
        if (!ev.compararFijo("Diagnóstico vigente (P-D1)", verdict.valid(), Operador.EQ, true)) {
            return Optional.of(RuleAction.noopInfo(ruleName, String.format(
                    "El diagnóstico de %s (%s) no sirve para decidir: %s.", sector.getId(), stateLabel, verdict.reason())));
        }
        return Optional.empty();
    }
}
