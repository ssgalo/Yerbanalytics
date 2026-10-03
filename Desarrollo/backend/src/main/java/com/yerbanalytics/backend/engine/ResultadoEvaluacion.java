package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;

import java.util.List;

/** Lo que devuelve el {@link RuleOrchestrator}: las acciones a ejecutar y la traza de cómo se llegó a ellas. */
public record ResultadoEvaluacion(List<RuleAction> acciones, TrazaEvaluacion traza) {}
