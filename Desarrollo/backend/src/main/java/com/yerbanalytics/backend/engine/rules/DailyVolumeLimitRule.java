package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DailyVolumeLimitRule implements Rule {

    private static final int PRIORITY = 4;
    private static final String NAME = "DailyVolumeLimitRule";
    private static final long MS_PER_24H = 24L * 60 * 60 * 1000;

    private final HistorialRepository historialRepository;

    public DailyVolumeLimitRule(HistorialRepository historialRepository) {
        this.historialRepository = historialRepository;
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
        return "🛑 Límite de volumen de riego diario";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.MAX_RIEGOS_24H);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        ConfiguracionOperativaEntity config = ctx.config();

        if (config == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin configuración operativa disponible — límite de volumen no verificado."));
        }

        long since = System.currentTimeMillis() - MS_PER_24H;
        long irrigationsIn24h = historialRepository.countByTipoAndSectorAndPeriod(
                ctx.sector().getId(), "Riego", since);

        // Fallback logic until real volume integration
        if (ev.comparar("Riegos en las últimas 24 h", (double) irrigationsIn24h, Operador.GE,
                ParametrosRiego.MAX_RIEGOS_24H)) {
            String reason = String.format(
                    "El sector %s ya recibió %d riegos en las últimas 24 h. " +
                    "Límite de volumen diario alcanzado. " +
                    "Riego autónomo bloqueado.",
                    ctx.sector().getId(), irrigationsIn24h);
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, reason));
        }

        return List.of(RuleAction.noopInfo(NAME,
                String.format("Historial de riegos en últimas 24 h para el sector %s dentro del límite.",
                              ctx.sector().getId())));
    }
}
