package com.yerbanalytics.backend.engine.overrides;

/**
 * Una pieza controlable junto con su override de demo y los dos estados que lo rodean.
 *
 * @param operationalActive lo que decidió el vivero (sin la capa de demo)
 * @param effectiveEnabled  si la pieza corre ahora: el override explícito de demo, si existe, y si no el operativo
 */
public record ControllableState(DemoControllable controllable, RuleOverride override,
                                boolean operationalActive, boolean effectiveEnabled) {
}
