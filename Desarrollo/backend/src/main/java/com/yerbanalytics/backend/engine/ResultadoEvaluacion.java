package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;

import java.util.List;

/**
 * Lo que devuelve el {@link RuleOrchestrator}: las acciones a ejecutar, la traza de cómo se llegó a ellas y si se
 * evaluó alguna regla que usa el diagnóstico de IA (entonces el diagnóstico del sector queda usado).
 *
 * <p>Si una de esas reglas pidió una aplicación por el diagnóstico, {@code usoDiagnostico} es {@code false}: el diagnóstico
 * lo gasta el {@code ApplicationService} cuando la aplicación se ejecuta (o se rechaza), no el motor al pedirla.
 */
public record ResultadoEvaluacion(List<RuleAction> acciones, TrazaEvaluacion traza, boolean usoDiagnostico) {

    /** Sin reglas de diagnóstico evaluadas. */
    public ResultadoEvaluacion(List<RuleAction> acciones, TrazaEvaluacion traza) {
        this(acciones, traza, false);
    }
}
