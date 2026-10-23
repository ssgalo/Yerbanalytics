package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.chlorosis.ChlorosisNutritionalRule;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DeclaredParameters;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.parametros.ParametrosEfectividad;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.supply.Supply;
import com.yerbanalytics.backend.supply.ValidDiagnosis;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * La política de la evaluación de efectividad (reglas_v2 §9): cuánto se espera para juzgar cada acción, cuánto tiene que subir
 * la humedad, qué es un diagnóstico vigente para comparar, y qué aplicaciones abren un seguimiento. Quien recorre los
 * seguimientos y los resuelve es {@link EffectivenessJob}; esto solo responde las preguntas de política.
 *
 * <p>Es {@link DemoControllable}: el simulador puede apagarlo (no se evalúa nada y no se bloquea nada) o acortar las esperas con los
 * cooldowns {@code verification} (minutos hasta la lectura de verificación del riego), {@code fertilizer-latency} y
 * {@code fungicide-latency} (minutos de la espera de E-02 y E-03). {@code 0} elimina la espera.
 */
@Service
public class EffectivenessService implements DemoControllable, ConsumidorParametros, FollowUpPolicy {

    public static final String NAME = "EffectivenessService";

    public static final String COOLDOWN_VERIFICATION = "verification";
    public static final String COOLDOWN_FERTILIZER = "fertilizer-latency";
    public static final String COOLDOWN_FUNGICIDE = "fungicide-latency";

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private final RuleOverrides overrides;
    private final HistorialRepository history;
    private final SectorNeighbors neighbors;
    private final DeclaredParameters params;

    public EffectivenessService(// Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros) para armarse.
                                @Lazy CatalogoParametrosService catalog,
                                RuleOverrides overrides,
                                HistorialRepository history,
                                SectorNeighbors neighbors) {
        this.overrides = overrides;
        this.history = history;
        this.neighbors = neighbors;
        this.params = new DeclaredParameters(this, catalog::vigentes);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "📈 Evaluación de efectividad";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.SEGUIMIENTO;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public Set<String> cooldownKeys() {
        return Set.of(COOLDOWN_VERIFICATION, COOLDOWN_FERTILIZER, COOLDOWN_FUNGICIDE);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosEfectividad.IRRIGATION_DELTA, ParametrosEfectividad.VERIFICATION_MINUTES,
                ParametrosEfectividad.FERTILIZER_LATENCY_DAYS, ParametrosEfectividad.FUNGICIDE_LATENCY_DAYS,
                ParametrosEfectividad.NO_DIAGNOSIS_GRACE_HOURS,
                ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS);
    }

    /** {@code false} si el simulador apagó la evaluación: no se evalúa ninguna acción y por lo tanto no se bloquea nada. */
    public boolean isOn() {
        return overrides.isEnabled(NAME);
    }

    @Override
    public long latencyMs(EffectivenessKind kind) {
        return switch (kind) {
            case IRRIGATION -> overridden(COOLDOWN_VERIFICATION)
                    .orElseGet(() -> params.number(ParametrosEfectividad.VERIFICATION_MINUTES) * MINUTE_MS).longValue();
            case CORRECTIVE_FERTILIZER -> overridden(COOLDOWN_FERTILIZER)
                    .orElseGet(() -> params.number(ParametrosEfectividad.FERTILIZER_LATENCY_DAYS) * DAY_MS).longValue();
            case FUNGICIDE -> overridden(COOLDOWN_FUNGICIDE)
                    .orElseGet(() -> params.number(ParametrosEfectividad.FUNGICIDE_LATENCY_DAYS) * DAY_MS).longValue();
        };
    }

    private Optional<Double> overridden(String key) {
        return overrides.cooldownMinutes(NAME, key).map(minutes -> minutes * MINUTE_MS);
    }

    /** Cuántos puntos tiene que subir la humedad de sustrato para que un riego sea efectivo (E-01). */
    public double irrigationDelta() {
        return params.number(ParametrosEfectividad.IRRIGATION_DELTA);
    }

    /** Cuánto más se espera un diagnóstico vigente, vencida la espera, antes de dar la acción por sin efectividad (E-02, E-03). */
    public long noDiagnosisGraceMs() {
        return Math.round(params.number(ParametrosEfectividad.NO_DIAGNOSIS_GRACE_HOURS) * HOUR_MS);
    }

    /** El diagnóstico del sector tal como lo ve la comparación: vigente (P-D1) o no, con el motivo. */
    public DiagnosisView diagnosisOf(SectorEntity sector, Instant now) {
        ValidDiagnosis.Verdict verdict = ValidDiagnosis.of(sector.getDiagnosisEstado(), sector.getDiagnosisConf(),
                sector.getDiagnosisTs(), now, params.number(ParametrosDiagnostico.CONFIANZA_MINIMA),
                params.number(ParametrosDiagnostico.VALIDITY_HOURS));
        if (!verdict.valid()) {
            return new DiagnosisView(null, null, false, verdict.reason());
        }
        return new DiagnosisView(sector.getDiagnosisEstado(), Severity.parse(sector.getDiagnosisSev()).orElse(null), true, null);
    }

    /**
     * El seguimiento que abre una aplicación de insumo, si corresponde: el fitosanitario, y el fertilizante sólo si es el
     * correctivo de la clorosis (F-C2; el plan de nutrición no se evalúa). Una racha de aplicaciones se evalúa una sola vez, desde
     * la primera, y sólo si el sector tiene un diagnóstico anómalo vigente contra el cual comparar.
     */
    public Optional<FollowUp> followUpFor(SectorEntity sector, Supply supply, String originRule, Instant now) {
        EffectivenessKind kind = kindOf(supply, originRule);
        if (kind == null || kind.isZoneScoped()) {
            return Optional.empty();
        }
        DiagnosisView diagnosis = diagnosisOf(sector, now);
        if (!diagnosis.valid() || DiagnosisView.HEALTHY.equals(diagnosis.state())) {
            return Optional.empty();
        }
        if (history.existsBySectorIdAndEfectividadTipoAndEvoShowTrueAndEvoEvaluadoTsIsNull(sector.getId(), kind.name())) {
            return Optional.empty();
        }
        Set<String> affected = kind == EffectivenessKind.FUNGICIDE
                ? neighbors.affectedAround(sector, neighbor -> hasSameDiagnosis(neighbor, diagnosis.state(), now))
                : Set.of();
        return Optional.of(new FollowUp(kind, new TreatmentBaseline(diagnosis.state(), diagnosis.severity(), affected)));
    }

    /** Los lindantes que tienen hoy el daño que se evalúa: mismo diagnóstico y vigente. Lo usa E-03 al comparar. */
    public boolean hasSameDiagnosis(SectorEntity sector, String state, Instant now) {
        DiagnosisView diagnosis = diagnosisOf(sector, now);
        return diagnosis.valid() && state.equals(diagnosis.state());
    }

    private static EffectivenessKind kindOf(Supply supply, String originRule) {
        if (supply == Supply.FUNGICIDE) {
            return EffectivenessKind.FUNGICIDE;
        }
        return ChlorosisNutritionalRule.NAME.equals(originRule) ? EffectivenessKind.CORRECTIVE_FERTILIZER : null;
    }
}
