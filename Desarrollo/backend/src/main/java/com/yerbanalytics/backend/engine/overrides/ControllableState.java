package com.yerbanalytics.backend.engine.overrides;

/**
 * Una pieza controlable junto con su override de demo (cooldowns y bypass) y su estado de encendido.
 *
 * @param enabled si la pieza corre ahora: el estado operativo, el que se cambia en Motor de reglas (la demo no lo toca)
 */
public record ControllableState(DemoControllable controllable, RuleOverride override, boolean enabled) {
}
