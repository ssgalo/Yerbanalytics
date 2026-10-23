package com.yerbanalytics.backend.engine.overrides;

import java.util.HashSet;
import java.util.Set;

/**
 * Doble de prueba de {@link RuleOverrides}: cooldowns y bypass de la capa de demo, y el estado operativo reducido a un
 * conjunto de piezas apagadas ({@link #turnOff}); todo lo demás está prendido. Para los tests de una pieza que no
 * necesitan la base ni {@code OperationalRuleStates}.
 */
public class TestRuleOverrides extends RuleOverridesStore implements RuleOverrides {

    private final Set<String> off = new HashSet<>();

    /** Apaga la pieza, como lo haría el vivero desde Motor de reglas. */
    public void turnOff(String ruleName) {
        off.add(ruleName);
    }

    @Override
    public boolean isEnabled(String ruleName) {
        return !off.contains(ruleName);
    }

    @Override
    public Set<String> inactiveNames() {
        return Set.copyOf(off);
    }
}
