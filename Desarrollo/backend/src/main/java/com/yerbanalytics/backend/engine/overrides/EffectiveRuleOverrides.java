package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * El único {@link RuleOverrides} del sistema: lo que el motor, el despacho de riego y los servicios consultan para saber
 * si una pieza corre.
 *
 * <pre>
 * isEnabled(n)          = estado operativo (única fuente de verdad del encendido)
 * cooldownMinutes(n, k) = demo
 * isBypassed(n, k)      = demo
 * </pre>
 *
 * La capa de demo sólo existe con {@code yerbanalytics.demo.enabled=true} y trae únicamente cooldowns y bypass: no
 * tiene interruptor, así que prender o apagar una pieza se hace sólo en Motor de reglas.
 */
@Component
public class EffectiveRuleOverrides implements RuleOverrides {

    private final RuleOverridesStore demo;
    private final OperationalRuleStates operational;

    public EffectiveRuleOverrides(RuleOverridesStore demo, OperationalRuleStates operational) {
        this.demo = demo;
        this.operational = operational;
    }

    @Override
    public boolean isEnabled(String ruleName) {
        return operational.isActive(ruleName);
    }

    @Override
    public Optional<Double> cooldownMinutes(String ruleName, String key) {
        return demo.cooldownMinutes(ruleName, key);
    }

    @Override
    public boolean isBypassed(String ruleName, String key) {
        return demo.isBypassed(ruleName, key);
    }

    @Override
    public Set<String> inactiveNames() {
        return operational.inactiveNames();
    }
}
