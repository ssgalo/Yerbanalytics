package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.traza.AccionTrazada;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Coordinador del motor de reglas.
 *
 * <p>Recibe todas las implementaciones de {@link Rule} registradas como
 * {@code @Component} en el contexto de Spring y las ordena <b>ascendentemente</b>
 * por {@link Rule#priority()} en el constructor. Sin este ordenamiento explícito,
 * Spring inyecta la lista en orden de declaración, lo que produciría un corte
 * anticipado en orden arbitrario (bug silencioso).
 *
 * <p>Por cada ciclo de evaluación ({@link #evaluate(RuleContext, OrigenEvaluacion)}):
 * <ol>
 *   <li>Itera las reglas en orden de prioridad.</li>
 *   <li>Acumula las acciones devueltas por cada regla.</li>
 *   <li>Si alguna acción es <em>bloqueante</em> ({@link ActionType#isBlocking()}),
 *       detiene la iteración para ese sector.</li>
 * </ol>
 */
@Service
public class RuleOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RuleOrchestrator.class);

    private final List<Rule> rules;
    private final CatalogoParametrosService parametros;

    public RuleOrchestrator(List<Rule> rules, CatalogoParametrosService parametros) {
        this.parametros = parametros;
        // Ordenamiento explícito obligatorio — Spring no ordena @Component por prioridad.
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(Rule::priority))
                .toList();
        log.info("RuleOrchestrator inicializado con {} regla(s): {}",
                this.rules.size(),
                this.rules.stream().map(Rule::name).toList());
    }

    /**
     * Evalúa todas las reglas en orden de prioridad para el sector dado.
     * En lugar de detenerse completamente ante el primer bloqueo,
     * utiliza un modelo de ramas (DAG) donde el bloqueo de un subsistema
     * (ej. RIEGO) no afecta a los demás (ej. MEDIASOMBRA).
     *
     * <p>Además de las acciones arma la traza: TODAS las reglas figuran, las que no corrieron con
     * el motivo ({@code OMITIDA_RAMA_BLOQUEADA} o {@code NO_ALCANZADA}) y la regla que cortó.
     * Los valores vigentes del catálogo se toman UNA vez, así todo el ciclo ve lo mismo.
     *
     * @param ctx    snapshot inmutable del sector (nunca null)
     * @param origen qué disparó la evaluación; la traza se guarda por origen
     * @return acciones acumuladas (puede estar vacía, nunca null) y la traza de la evaluación
     */
    public ResultadoEvaluacion evaluate(RuleContext ctx, OrigenEvaluacion origen) {
        List<RuleAction> accumulated = new ArrayList<>();
        List<TrazaRegla> trazas = new ArrayList<>(rules.size());
        String sectorId = ctx.sector().getId();
        ParametrosVigentes vigentes = parametros.vigentes();

        // Nombre de la regla que cortó cada nivel; null = no cortado.
        String abortAll = null;
        String abortRiego = null;
        String abortInsumo = null;

        for (Rule rule : rules) {
            RuleBranch branch = rule.branch();

            // Si hay un bloqueo global, no se evalúa nada más.
            if (abortAll != null) {
                trazas.add(omitida(rule, EstadoRegla.NO_ALCANZADA, abortAll));
                continue;
            }

            // Saltear evaluación si la rama específica ya fue bloqueada por una regla anterior
            if (branch == RuleBranch.RIEGO && abortRiego != null) {
                trazas.add(omitida(rule, EstadoRegla.OMITIDA_RAMA_BLOQUEADA, abortRiego));
                continue;
            }
            if (branch == RuleBranch.INSUMO && abortInsumo != null) {
                trazas.add(omitida(rule, EstadoRegla.OMITIDA_RAMA_BLOQUEADA, abortInsumo));
                continue;
            }

            Evaluacion ev = new Evaluacion(rule, vigentes);
            List<RuleAction> actions = rule.evaluate(ctx, ev);
            accumulated.addAll(actions);
            trazas.add(new TrazaRegla(rule.name(), branch, rule.priority(), EstadoRegla.EVALUADA,
                    List.copyOf(ev.comparaciones()),
                    actions.stream().map(a -> new AccionTrazada(a.type(), a.motivo())).toList(),
                    null));

            // Analizar acciones para actualizar el estado de los bloqueos de rama
            for (RuleAction action : actions) {
                ActionType type = action.type();
                if (type == ActionType.ABORT_ALL) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió ABORT_ALL — ejecución global detenida.",
                            sectorId, rule.name(), branch);
                    abortAll = rule.name();
                } else if (type == ActionType.ABORT_RIEGO || type == ActionType.POSTPONE_RIEGO) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió {} — rama RIEGO detenida.",
                            sectorId, rule.name(), branch, type);
                    abortRiego = rule.name();
                } else if (type == ActionType.ABORT_INSUMO) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió ABORT_INSUMO — rama INSUMO detenida.",
                            sectorId, rule.name(), branch);
                    abortInsumo = rule.name();
                }
            }
        }

        TrazaEvaluacion traza = new TrazaEvaluacion(
                sectorId,
                ctx.zona() != null ? ctx.zona().getId() : null,
                origen,
                ctx.now(),
                Integer.toHexString(vigentes.valores().hashCode()),
                List.copyOf(trazas));
        return new ResultadoEvaluacion(accumulated, traza);
    }

    private static TrazaRegla omitida(Rule rule, EstadoRegla estado, String bloqueadaPor) {
        return new TrazaRegla(rule.name(), rule.branch(), rule.priority(), estado,
                List.of(), List.of(), bloqueadaPor);
    }

    /** Expone las reglas ordenadas (útil para tests de ordenamiento). */
    public List<Rule> getRules() {
        return rules;
    }
}
