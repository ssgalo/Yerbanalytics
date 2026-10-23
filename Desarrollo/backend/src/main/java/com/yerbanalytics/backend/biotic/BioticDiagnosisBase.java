package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
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
import com.yerbanalytics.backend.engine.parametros.ParametrosBiotico;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.supply.Supply;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El esqueleto común de las reglas que tratan un diagnóstico biótico del sector con fitosanitario (F-F1 hongo, F-P1 plagas).
 * Las dos deciden igual y sólo difieren en <i>qué ambiente favorece</i> al problema:
 * <ol>
 *   <li>El diagnóstico del sector es del tipo de la regla y está vigente (P-D1: reciente, concluyente y con confianza).</li>
 *   <li>Se aplica si se cumple alguna de tres condiciones: severidad moderada o alta; el ambiente lo favorece
 *       ({@link #favorableEnvironment}); o el mismo diagnóstico se repite en N capturas diarias seguidas.</li>
 *   <li>Si no se cumple ninguna, espera a las próximas capturas ({@link #whenWaiting}).</li>
 * </ol>
 *
 * <p><b>Qué decide y qué no.</b> Decide <i>si</i> corresponde tratar; <i>cuándo</i> se aplica lo resuelve el
 * {@code ApplicationService} con las precondiciones de aplicación (ventana, temperatura, lluvia, humedad, intervalo y tope).
 * La regla pide la aplicación con {@code fromDiagnosis = true}: el servicio vuelve a verificar la vigencia (P-D1) al reintentar.
 *
 * <p><b>Una vez por diagnóstico.</b> {@link #usaDiagnostico()}: sólo corre cuando el sector tiene un diagnóstico nuevo. El
 * diagnóstico se consume cuando la aplicación se ejecuta, no cuando se pide (ver {@code ApplicationService}).
 *
 * <p><b>Simulador:</b> el bypass {@value #BYPASS_PERSISTENCE} da el diagnóstico por visto en las capturas seguidas que pide
 * el catálogo.
 */
abstract class BioticDiagnosisBase implements Rule {

    /** Bypass key: the diagnosis counts as seen in the consecutive captures the catalog asks for. */
    public static final String BYPASS_PERSISTENCE = "persistence";

    private final BioticKind kind;
    private final DiagnosisHistory diagnoses;

    protected BioticDiagnosisBase(BioticKind kind, DiagnosisHistory diagnoses) {
        this.kind = kind;
        this.diagnoses = diagnoses;
    }

    @Override
    public final RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    /** Decide con el diagnóstico de IA: actúa una vez por diagnóstico nuevo. */
    @Override
    public final boolean usaDiagnostico() {
        return true;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_PERSISTENCE);
    }

    /**
     * Si el ambiente de la macro-zona favorece al problema, por qué (para el motivo de la acción); vacío si no. Lee las
     * lecturas por {@code ReadingHistory} y registra cada comparación en la traza. Los parámetros que lee los declara la
     * regla concreta en su {@code parametros()}, junto con los comunes: la confianza y la vigencia del diagnóstico
     * (P-D1) y {@code biotico.capturas-seguidas}.
     */
    protected abstract Optional<String> favorableEnvironment(RuleContext ctx, Evaluacion ev);

    /** El texto de la alerta cuando se aplica (el nivel lo da la severidad). */
    protected abstract String alertText(String sectorId);

    /** Qué hace la regla cuando no se cumple ninguna condición. Por defecto sólo deja el Registro de Inacción. */
    protected List<RuleAction> whenWaiting(SectorEntity sector, String reason) {
        return List.of(RuleAction.noopInfo(name(), reason));
    }

    @Override
    public final List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        SectorEntity sector = ctx.sector();
        String sectorId = sector.getId();

        Optional<RuleAction> notApplicable = DiagnosisGate.check(ctx, ev, name(), kind.label(), kind::matches);
        if (notApplicable.isPresent()) {
            return List.of(notApplicable.get());
        }
        double minConfidence = ev.numero(ParametrosDiagnostico.CONFIANZA_MINIMA);

        Severity severity = Severity.parse(sector.getDiagnosisSev()).orElse(Severity.MILD);
        Optional<String> why = severityReason(ev, severity)
                .or(() -> favorableEnvironment(ctx, ev))
                .or(() -> persistenceReason(ctx, ev, minConfidence));
        if (why.isEmpty()) {
            return whenWaiting(sector, String.format(
                    "%s leve en %s, sin un ambiente que lo favorezca y sin repetirse en capturas seguidas: se espera a las próximas capturas.",
                    capitalized(kind.label()), sectorId));
        }

        String order = String.format("%s en %s (%s): se pide fitosanitario.", capitalized(kind.label()), sectorId, why.get());
        String alert = alertText(sectorId);
        NivelAlerta level = severity.isHigh() ? NivelAlerta.CRITICAL : NivelAlerta.WARNING;
        return List.of(
                RuleAction.of(ActionType.ACTIVAR_BOMBA, name(), order, new DetalleInsumo(Supply.FUNGICIDE, true)),
                new RuleAction(ActionType.ALERTA, name(), alert, new DetalleAlerta(level, alert)));
    }

    private Optional<String> severityReason(Evaluacion ev, Severity severity) {
        return ev.compararFijo("Severidad moderada o alta", severity.isModerateOrHigher(), Operador.EQ, true)
                ? Optional.of("severidad " + severity.name().toLowerCase())
                : Optional.empty();
    }

    /** «El mismo diagnóstico en N capturas diarias seguidas»; el bypass del simulador lo da por cumplido. */
    private Optional<String> persistenceReason(RuleContext ctx, Evaluacion ev, double minConfidence) {
        int required = (int) Math.round(ev.numero(ParametrosBiotico.CONSECUTIVE_CAPTURES));
        if (ctx.isBypassed(name(), BYPASS_PERSISTENCE)) {
            ev.compararFijo("Capturas seguidas con el diagnóstico (bypass del simulador)", required, Operador.GE, required);
            return Optional.of(required + " capturas seguidas");
        }
        List<ConsecutiveCaptures.Capture> recent = diagnoses.recent(ctx.sector().getId(), ctx.now(), required + 1);
        int streak = ConsecutiveCaptures.count(recent,
                c -> kind.matches(c.estado()) && c.conf() != null && c.conf() >= minConfidence, ZonaHorariaVivero.ZONA);
        return ev.comparar("Capturas diarias seguidas con el diagnóstico", (double) streak, Operador.GE,
                ParametrosBiotico.CONSECUTIVE_CAPTURES)
                ? Optional.of(streak + " capturas diarias seguidas")
                : Optional.empty();
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
