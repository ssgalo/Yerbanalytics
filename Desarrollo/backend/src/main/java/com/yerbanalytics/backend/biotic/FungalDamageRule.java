package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.RuleObjective;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosBiotico;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * F-F1 · Daño fúngico (reglas_v2 §8.5): ante un diagnóstico Daño fúngico vigente pide fitosanitario y avisa con un
 * {@code WARNING} «Alta probabilidad de hongo» ({@code CRITICAL} con severidad alta) cuando se cumple alguna de estas
 * condiciones: severidad moderada o alta; sustrato por encima de {@code biotico.humedad-hongo} o DPV por debajo de
 * {@code biotico.dpv-saturado} en las dos últimas lecturas de pulso, ambas de las últimas 24 h; el mismo diagnóstico en
 * {@code biotico.capturas-seguidas} capturas diarias seguidas. Si no, espera a las próximas capturas.
 *
 * <p>Ver {@link BioticDiagnosisBase} para el esqueleto compartido con F-P1.
 */
@Component
public class FungalDamageRule extends BioticDiagnosisBase {

    /** Prioridad: después de la mediasombra y antes del seguimiento; única entre las reglas por sector. */
    private static final int PRIORITY = 15;
    public static final String NAME = "FungalDamageRule";

    /** Las dos lecturas «seguidas» que pide la v2, dentro de las últimas 24 h. */
    private static final int CONSECUTIVE_READINGS = 2;
    private static final Duration READINGS_WINDOW = Duration.ofHours(24);

    private final ReadingHistory readings;

    public FungalDamageRule(ReadingHistory readings, DiagnosisHistory diagnoses) {
        super(BioticKind.FUNGAL, diagnoses);
        this.readings = readings;
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
        return "Daño fúngico";
    }

    @Override
    public String code() {
        return "F-F1";
    }

    @Override
    public RuleObjective objective() {
        return RuleObjective.SANITY;
    }

    @Override
    public RuleEffect effect() {
        return RuleEffect.ACTS;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS,
                ParametrosBiotico.CONSECUTIVE_CAPTURES, ParametrosBiotico.FUNGAL_MOISTURE, ParametrosBiotico.DPV_SATURATED);
    }

    @Override
    protected Optional<String> favorableEnvironment(RuleContext ctx, Evaluacion ev) {
        Instant since = ctx.now().minus(READINGS_WINDOW);
        List<ZoneReadingEntity> last = readings.latest(ctx.zona().getId(), CONSECUTIVE_READINGS, true).stream()
                .filter(r -> !Instant.ofEpochMilli(r.getTs()).isBefore(since))
                .toList();
        if (!ev.compararFijo("Lecturas de pulso de las últimas 24 h", last.size(), Operador.GE, CONSECUTIVE_READINGS)) {
            return Optional.empty();
        }
        ZoneReadingEntity previous = last.get(last.size() - 2);
        ZoneReadingEntity latest = last.get(last.size() - 1);

        boolean wet = ev.comparar("Humedad de sustrato (lectura anterior)", ReadingMetric.HUM_SUS.valueIn(previous),
                Operador.GT, ParametrosBiotico.FUNGAL_MOISTURE)
                && ev.comparar("Humedad de sustrato (última lectura)", ReadingMetric.HUM_SUS.valueIn(latest),
                Operador.GT, ParametrosBiotico.FUNGAL_MOISTURE);
        if (wet) {
            return Optional.of(String.format("sustrato por encima del %.0f %% en dos lecturas seguidas",
                    ev.numero(ParametrosBiotico.FUNGAL_MOISTURE)));
        }
        boolean saturated = ev.comparar("DPV (lectura anterior)", ReadingMetric.DPV.valueIn(previous),
                Operador.LT, ParametrosBiotico.DPV_SATURATED)
                && ev.comparar("DPV (última lectura)", ReadingMetric.DPV.valueIn(latest),
                Operador.LT, ParametrosBiotico.DPV_SATURATED);
        return saturated
                ? Optional.of(String.format("aire saturado: DPV por debajo de %.2f kPa en dos lecturas seguidas",
                ev.numero(ParametrosBiotico.DPV_SATURATED)))
                : Optional.empty();
    }

    @Override
    protected String alertText(String sectorId) {
        return "Alta probabilidad de hongo en " + sectorId;
    }
}
