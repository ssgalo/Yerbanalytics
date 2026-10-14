package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;

import java.util.List;

/**
 * Lo que devuelve el {@link RuleOrchestrator}: las acciones a ejecutar, la traza de cómo se llegó a ellas y si se
 * evaluó alguna regla que usa el diagnóstico de IA (entonces el diagnóstico del sector queda usado).
 */
public record ResultadoEvaluacion(List<RuleAction> acciones, TrazaEvaluacion traza, boolean usoDiagnostico) {

    /** Sin reglas de diagnóstico evaluadas. */
    public ResultadoEvaluacion(List<RuleAction> acciones, TrazaEvaluacion traza) {
        this(acciones, traza, false);
    }
}
