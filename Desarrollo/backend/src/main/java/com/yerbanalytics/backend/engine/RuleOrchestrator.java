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
     *
     * @param ctx snapshot inmutable del sector (nunca null)
     * @return lista de acciones acumuladas; puede estar vacía pero nunca es null
     */
    public List<RuleAction> evaluate(RuleContext ctx) {
        List<RuleAction> accumulated = new ArrayList<>();
        String sectorId = ctx.sector().getId();

        for (Rule rule : rules) {
            List<RuleAction> actions = rule.evaluate(ctx);
            accumulated.addAll(actions);

            boolean hasBlocking = actions.stream()
                    .anyMatch(a -> a.type().isBlocking());

            if (hasBlocking) {
                log.debug("Sector {}: regla '{}' emitió acción bloqueante — cadena detenida.",
                        sectorId, rule.name());
                break;
            }
        }

        return accumulated;
    }

    /** Expone las reglas ordenadas (útil para tests de ordenamiento). */
    public List<Rule> getRules() {
        return rules;
    }
}
