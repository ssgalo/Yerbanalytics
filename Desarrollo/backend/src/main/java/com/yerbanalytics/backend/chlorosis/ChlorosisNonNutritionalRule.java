package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.RuleObjective;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelPendingApplication;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DiagnosisGate;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosClorosis;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import com.yerbanalytics.backend.readings.ReadingWindow;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * F-C1 · Clorosis con causa no nutricional (reglas_v2 §8.5): la clorosis es un síntoma con varias causas, y antes de
 * fertilizar se descartan las que el fertilizante no corrige. Ante un diagnóstico Clorosis vigente con alguna de estas:
 * <ul>
 *   <li>humedad de sustrato media de las últimas {@code clorosis.humedad-horas} (48) horas {@code >} {@code clorosis.humedad-asfixia}
 *       (80 %): probable <b>asfixia radicular</b>;</li>
 *   <li>CE {@code ≥} {@code clorosis.ce-salinidad} (2,0 dS/m): probable <b>salinidad</b>;</li>
 *   <li>pH {@code ≥} {@code clorosis.ph-ferrica} (6,5): probable <b>clorosis férrica</b> por pH alto,</li>
 * </ul>
 * no se aplica fertilizante correctivo en el sector y se avisa con un {@code WARNING} que dice la causa.
 *
 * <p><b>Decide la compuerta, ejecuta F-C2.</b> F-C1 no aplica nada: corta la rama de insumos del sector
 * ({@code ABORT_INSUMO}) y F-C2, que viene después, queda sin alcanzar. El corte retira además el pedido de F-C2 que hubiera
 * quedado pendiente de un diagnóstico anterior, porque esperar su ventana lo aplicaría igual.
 *
 * <p>Lee la CE y el pH de la lectura vigente de la zona y la humedad del historial de lecturas; una métrica sin dato (o
 * descartada por S-03) no es una causa.
 */
@Component
public class ChlorosisNonNutritionalRule implements Rule {

    /** Prioridad: antes que F-C2, a quien le corta la rama; única entre las reglas por sector y por macro-zona. */
    private static final int PRIORITY = 24;
    public static final String NAME = "ChlorosisNonNutritionalRule";

    private final ReadingHistory readings;

    public ChlorosisNonNutritionalRule(ReadingHistory readings) {
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
        return "Clorosis con causa no nutricional";
    }

    @Override
    public String code() {
        return "F-C1";
    }

    @Override
    public RuleObjective objective() {
        return RuleObjective.NUTRITION;
    }

    @Override
    public RuleEffect effect() {
        return RuleEffect.WARNS;
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
        return List.of(ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS,
                ParametrosClorosis.WATERLOGGING_MOISTURE, ParametrosClorosis.WATERLOGGING_HOURS,
                ParametrosClorosis.SALINITY_EC, ParametrosClorosis.IRON_CHLOROSIS_PH);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Optional<RuleAction> notApplicable = DiagnosisGate.check(ctx, ev, NAME, "clorosis", "Clorosis"::equals);
        if (notApplicable.isPresent()) {
            return List.of(notApplicable.get());
        }
        String sectorId = ctx.sector().getId();

        List<ChlorosisCause> causes = new ArrayList<>();
        double hours = ev.numero(ParametrosClorosis.WATERLOGGING_HOURS);
        Double averageMoisture = readings.average(ctx.zona().getId(), ReadingMetric.HUM_SUS,
                ReadingWindow.since(ctx.now().minus(Duration.ofMinutes(Math.round(hours * 60))))).orElse(null);
        if (ev.comparar(String.format("Humedad de sustrato media de las últimas %.0f h", hours), averageMoisture,
                Operador.GT, ParametrosClorosis.WATERLOGGING_MOISTURE)) {
            causes.add(ChlorosisCause.ROOT_ASPHYXIA);
        }
        if (ev.comparar("CE del sustrato", ctx.validMetricRaw("ce"), Operador.GE, ParametrosClorosis.SALINITY_EC)) {
            causes.add(ChlorosisCause.SALINITY);
        }
        if (ev.comparar("pH del sustrato", ctx.validMetricRaw("phSuelo"), Operador.GE, ParametrosClorosis.IRON_CHLOROSIS_PH)) {
            causes.add(ChlorosisCause.IRON_CHLOROSIS);
        }

        if (causes.isEmpty()) {
            return List.of(RuleAction.noopInfo(NAME, "La clorosis de " + sectorId
                    + " no tiene una causa no nutricional: corresponde el fertilizante correctivo (F-C2)."));
        }

        String probable = causes.stream().map(ChlorosisCause::label).collect(Collectors.joining(", "));
        String remedy = causes.stream().map(ChlorosisCause::remedy).collect(Collectors.joining("; "));
        String text = String.format("Clorosis en %s con causa probable no nutricional: %s. No se aplica fertilizante (%s).",
                sectorId, probable, remedy);
        return List.of(
                RuleAction.of(ActionType.ABORT_INSUMO, NAME, text,
                        new CancelPendingApplication(Supply.FERTILIZER, ChlorosisNutritionalRule.NAME)),
                new RuleAction(ActionType.ALERTA, NAME, text, new DetalleAlerta(NivelAlerta.WARNING, text)));
    }
}
