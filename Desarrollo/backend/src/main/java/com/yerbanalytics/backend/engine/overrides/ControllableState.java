package com.yerbanalytics.backend.engine.overrides;

/** Una pieza controlable junto con el override que tiene hoy. */
public record ControllableState(DemoControllable controllable, RuleOverride override) {
}
