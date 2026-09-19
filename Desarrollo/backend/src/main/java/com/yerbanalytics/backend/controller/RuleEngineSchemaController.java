package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DagSchemaDto;
import com.yerbanalytics.backend.dto.RuleEdgeDto;
import com.yerbanalytics.backend.dto.RuleNodeDto;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint que expone la topología base del pipeline de reglas del motor agronómico.
 *
 * <p>El frontend consume este endpoint para obtener el esquema estático del DAG
 * (Grafo Dirigido Acíclico) y así poder renderizar interactivamente el proceso de
 * decisión al seleccionar un evento del historial.
 *
 * <p>La topología se genera dinámicamente a partir de las reglas autodescubiertas
 * por el {@link RuleOrchestrator}, por lo que si se agrega una nueva regla en el
 * futuro, el grafo del frontend se actualiza automáticamente sin cambios en el front.
 *
 * <p>El grafo generado tiene la siguiente estructura:
 * <ul>
 *   <li><b>Nodo "start":</b> punto de entrada del pipeline.</li>
 *   <li><b>Nodos de regla:</b> uno por cada {@link Rule} registrada, ordenadas por prioridad.</li>
 *   <li><b>Nodo "abort":</b> destino de las aristas de bloqueo (cuando una regla aborta la cadena).</li>
 *   <li><b>Nodo "success":</b> destino final cuando todas las reglas se evalúan sin bloqueo.</li>
 *   <li><b>Arista "Continúa":</b> de cada regla a la siguiente (flujo no bloqueante).</li>
 *   <li><b>Arista "Bloquea":</b> de cada regla al nodo "abort" (flujo bloqueante).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rules")
public class RuleEngineSchemaController {

    private final RuleOrchestrator ruleOrchestrator;

    public RuleEngineSchemaController(RuleOrchestrator ruleOrchestrator) {
        this.ruleOrchestrator = ruleOrchestrator;
    }

    /**
     * Devuelve el esquema base del DAG del motor de reglas.
     *
     * <p>La topología es estática (no cambia entre requests) y se genera a partir de
     * las reglas registradas en el {@link RuleOrchestrator}, por lo que si en el
     * futuro se agrega una regla nueva el grafo se actualiza sin cambios en el frontend.
     *
     * @return {@link DagSchemaDto} con la lista de nodos y aristas del DAG
     */
    @GetMapping("/schema")
    public ResponseEntity<DagSchemaDto> getSchema() {
        List<Rule> rules = ruleOrchestrator.getRules(); // ya vienen ordenadas por priority()

        List<RuleNodeDto> nodes = new ArrayList<>();
        List<RuleEdgeDto> edges = new ArrayList<>();

        // Nodo de inicio (no es una regla, es el punto de entrada global)
        nodes.add(new RuleNodeDto("start", "Inicio Evaluación", "input", -1, "GLOBAL"));
        
        // Nodo de aborto global
        nodes.add(new RuleNodeDto("abort-GLOBAL", "Pipeline Detenido", "output", 999, "GLOBAL"));

        // Nodos de aborto y éxito por rama
        for (com.yerbanalytics.backend.engine.RuleBranch branch : com.yerbanalytics.backend.engine.RuleBranch.values()) {
            if (branch == com.yerbanalytics.backend.engine.RuleBranch.GLOBAL) continue;
            nodes.add(new RuleNodeDto("abort-" + branch.name(), "Bloqueo " + branch.name(), "output", 999, branch.name()));
            nodes.add(new RuleNodeDto("success-" + branch.name(), "Evaluado OK", "output", 1000, branch.name()));
        }

        // Agrupar reglas por rama, preservando el orden de prioridad interno
        java.util.Map<com.yerbanalytics.backend.engine.RuleBranch, List<Rule>> rulesByBranch = new java.util.EnumMap<>(com.yerbanalytics.backend.engine.RuleBranch.class);
        for (Rule rule : rules) {
            rulesByBranch.computeIfAbsent(rule.branch(), k -> new ArrayList<>()).add(rule);
        }

        // 1. Cadena Global
        String lastGlobalId = "start";
        List<Rule> globalRules = rulesByBranch.getOrDefault(com.yerbanalytics.backend.engine.RuleBranch.GLOBAL, List.of());
        for (Rule rule : globalRules) {
            String ruleId = rule.name();
            nodes.add(new RuleNodeDto(ruleId, rule.label(), "default", rule.priority(), "GLOBAL"));
            edges.add(new RuleEdgeDto("e_" + lastGlobalId + "_" + ruleId, lastGlobalId, ruleId, "Continúa"));
            edges.add(new RuleEdgeDto("e_" + ruleId + "_abort", ruleId, "abort-GLOBAL", "Bloquea"));
            lastGlobalId = ruleId;
        }

        // 2. Ramas Independientes (parten del último nodo global)
        for (com.yerbanalytics.backend.engine.RuleBranch branch : com.yerbanalytics.backend.engine.RuleBranch.values()) {
            if (branch == com.yerbanalytics.backend.engine.RuleBranch.GLOBAL) continue;

            List<Rule> branchRules = rulesByBranch.getOrDefault(branch, List.of());
            if (branchRules.isEmpty()) continue; // Si no hay reglas en esta rama, no la dibujamos

            String prevNodeId = lastGlobalId;

            for (Rule rule : branchRules) {
                String ruleId = rule.name();
                nodes.add(new RuleNodeDto(ruleId, rule.label(), "default", rule.priority(), branch.name()));
                
                // Arista: prevNodeId -> esta regla
                edges.add(new RuleEdgeDto("e_" + prevNodeId + "_" + ruleId, prevNodeId, ruleId, "Continúa"));
                
                // Arista: esta regla -> nodo abort específico de su rama
                edges.add(new RuleEdgeDto("e_" + ruleId + "_abort", ruleId, "abort-" + branch.name(), "Bloquea"));
                
                prevNodeId = ruleId;
            }

            // Arista final: última regla de la rama -> success específico de la rama
            edges.add(new RuleEdgeDto("e_" + prevNodeId + "_success", prevNodeId, "success-" + branch.name(), "Completado"));
        }

        return ResponseEntity.ok(new DagSchemaDto(nodes, edges));
    }
}
