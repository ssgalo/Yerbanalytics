package com.yerbanalytics.backend.engine;

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
 * <p>Por cada ciclo de evaluación ({@link #evaluate(RuleContext)}):
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

    public RuleOrchestrator(List<Rule> rules) {
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
     * @param ctx snapshot inmutable del sector (nunca null)
     * @return lista de acciones acumuladas; puede estar vacía pero nunca es null
     */
    public List<RuleAction> evaluate(RuleContext ctx) {
        List<RuleAction> accumulated = new ArrayList<>();
        String sectorId = ctx.sector().getId();

        boolean abortAll = false;
        boolean abortRiego = false;
        boolean abortInsumo = false;

        for (Rule rule : rules) {
            // Si hay un bloqueo global, no se evalúa nada más.
            if (abortAll) {
                break;
            }

            RuleBranch branch = rule.branch();

            // Saltear evaluación si la rama específica ya fue bloqueada por una regla anterior
            if (branch == RuleBranch.RIEGO && abortRiego) {
                continue;
            }
            if (branch == RuleBranch.INSUMO && abortInsumo) {
                continue;
            }

            List<RuleAction> actions = rule.evaluate(ctx);
            accumulated.addAll(actions);

            // Analizar acciones para actualizar el estado de los bloqueos de rama
            for (RuleAction action : actions) {
                ActionType type = action.type();
                if (type == ActionType.ABORT_ALL) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió ABORT_ALL — ejecución global detenida.",
                            sectorId, rule.name(), branch);
                    abortAll = true;
                } else if (type == ActionType.ABORT_RIEGO || type == ActionType.POSTPONE_RIEGO) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió {} — rama RIEGO detenida.",
                            sectorId, rule.name(), branch, type);
                    abortRiego = true;
                } else if (type == ActionType.ABORT_INSUMO) {
                    log.debug("Sector {}: regla '{}' (rama {}) emitió ABORT_INSUMO — rama INSUMO detenida.",
                            sectorId, rule.name(), branch);
                    abortInsumo = true;
                }
            }
        }

        return accumulated;
    }

    /** Expone las reglas ordenadas (útil para tests de ordenamiento). */
    public List<Rule> getRules() {
        return rules;
    }
}
