package com.yerbanalytics.backend.engine.overrides;

/**
 * Doble de prueba de {@link RuleOverrides}: sólo la capa de demo, sin estado operativo (todo prendido salvo lo que el
 * test apague con {@link #put}). Para los tests de una pieza que no necesitan la base ni {@code OperationalRuleStates}.
 */
public class TestRuleOverrides extends RuleOverridesStore implements RuleOverrides {

    @Override
    public boolean isEnabled(String ruleName) {
        return explicitEnabled(ruleName).orElse(true);
    }
}
