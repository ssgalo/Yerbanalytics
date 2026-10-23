package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosBiotico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import com.yerbanalytics.backend.readings.ReadingWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F-P1 · Plaga foliar (reglas_v2 §8.5): ante un diagnóstico Plaga foliar (o Ácaro, que es una plaga foliar) vigente pide
 * fitosanitario con un {@code WARNING} ({@code CRITICAL} con severidad alta) cuando se cumple alguna condición:
 * severidad moderada o alta; DPV igual o mayor a {@code biotico.dpv-calor-seco} en alguna lectura de las últimas
 * {@code biotico.horas-dpv-acaros} horas; el mismo diagnóstico en {@code biotico.capturas-seguidas} capturas diarias
 * seguidas. Si no se cumple ninguna, deja un {@code INFO} y espera.
 *
 * <p>Ver {@link BioticDiagnosisRule} para el esqueleto compartido con F-F1.
 */
@Component
public class FoliarPestRule extends BioticDiagnosisRule {

    /** Prioridad: justo después de F-F1; única entre las reglas por sector. */
    private static final int PRIORITY = 16;
    public static final String NAME = "FoliarPestRule";

    private final ReadingHistory readings;

    public FoliarPestRule(ReadingHistory readings, DiagnosisHistory diagnoses) {
        super(BioticKind.PEST, diagnoses);
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
        return "🐛 Plaga foliar (F-P1)";
    }

    @Override
    protected List<DefinicionParametro> environmentParameters() {
        return List.of(ParametrosBiotico.DPV_DRY_HEAT, ParametrosBiotico.MITE_DPV_HOURS);
    }

    @Override
    protected Optional<String> favorableEnvironment(RuleContext ctx, Evaluacion ev) {
        double hours = ev.numero(ParametrosBiotico.MITE_DPV_HOURS);
        ReadingWindow window = ReadingWindow.since(ctx.now().minus(Duration.ofMinutes(Math.round(hours * 60))));
        Double peak = readings.max(ctx.zona().getId(), ReadingMetric.DPV, window).orElse(null);
        return ev.comparar(String.format("DPV máximo de las últimas %.0f h", hours), peak, Operador.GE,
                ParametrosBiotico.DPV_DRY_HEAT)
                ? Optional.of(String.format("calor seco: DPV de %.1f kPa en las últimas %.0f h", peak, hours))
                : Optional.empty();
    }

    @Override
    protected String alertText(String sectorId) {
        return "Alta probabilidad de plaga foliar en " + sectorId;
    }

    /** Además del Registro de Inacción, F-P1 deja un aviso informativo de la macro-zona. */
    @Override
    protected List<RuleAction> whenWaiting(SectorEntity sector, String reason) {
        List<RuleAction> actions = new ArrayList<>(super.whenWaiting(sector, reason));
        actions.add(new RuleAction(ActionType.ALERTA, name(), reason, new DetalleAlerta(NivelAlerta.INFO, reason)));
        return actions;
    }
}
