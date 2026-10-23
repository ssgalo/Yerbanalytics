package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * El único {@link RuleOverrides} del sistema: lo que el motor, el despacho de riego y los servicios consultan para saber
 * si una pieza corre.
 *
 * <pre>
 * isEnabled(n)          = override explícito de demo (si existe) ; si no, el estado operativo
 * cooldownMinutes(n, k) = demo
 * isBypassed(n, k)      = demo
 * </pre>
 *
 * El override de demo sólo existe con {@code yerbanalytics.demo.enabled=true}: sin modo demo la capa está vacía y el
 * efectivo es el operativo. Con modo demo, el override explícito del simulador gana, para prender o apagar (así el happy
 * path de Demo Expo puede prender una regla retirada). Un override que sólo trae cooldowns o bypass no decide el estado.
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
        return demo.explicitEnabled(ruleName).orElseGet(() -> operational.isActive(ruleName));
    }

    @Override
    public Optional<Double> cooldownMinutes(String ruleName, String key) {
        return demo.cooldownMinutes(ruleName, key);
    }

    @Override
    public boolean isBypassed(String ruleName, String key) {
        return demo.isBypassed(ruleName, key);
    }

    /** Lo que decidió el vivero, sin la capa de demo. */
    public boolean isOperationallyActive(String ruleName) {
        return operational.isActive(ruleName);
    }

    @Override
    public Set<String> inactiveNames() {
        Set<String> out = new HashSet<>(operational.inactiveNames());
        demo.snapshot().forEach((name, override) -> override.explicitEnabled().ifPresent(enabled -> {
            if (enabled) {
                out.remove(name);
            } else {
                out.add(name);
            }
        }));
        return out;
    }
}
