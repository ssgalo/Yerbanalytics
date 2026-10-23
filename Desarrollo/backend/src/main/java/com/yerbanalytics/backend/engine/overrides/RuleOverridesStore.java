package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capa de demo de los overrides de las piezas, en memoria. Nada en producción escribe acá: el único que escribe es el
 * control de demo ({@link DemoRuleControlService}), que existe sólo con {@code yerbanalytics.demo.enabled=true}. No se
 * persiste, así que un reinicio vuelve al comportamiento configurado.
 *
 * <p>Sólo guarda cooldowns y bypass. Si una pieza corre o no lo decide únicamente el estado operativo
 * ({@link OperationalRuleStates}, donde viven también las reglas retiradas): la demo no lo puede cambiar.
 */
@Component
public class RuleOverridesStore {

    private final Map<String, RuleOverride> overrides = new ConcurrentHashMap<>();

    public Optional<Double> cooldownMinutes(String ruleName, String key) {
        return Optional.ofNullable(get(ruleName).cooldownMinutes().get(key));
    }

    public boolean isBypassed(String ruleName, String key) {
        return get(ruleName).bypassed().contains(key);
    }

    /** The override of a rule, or {@link RuleOverride#NONE}. */
    public RuleOverride get(String ruleName) {
        return overrides.getOrDefault(ruleName, RuleOverride.NONE);
    }

    /** Replaces the override of a rule. */
    public void put(String ruleName, RuleOverride override) {
        overrides.put(ruleName, override);
    }

    /** Borra todos los overrides: cada pieza vuelve a sus cooldowns y guardas configurados. */
    public void clear() {
        overrides.clear();
    }

    /** Snapshot of the rules that have an override. */
    public Map<String, RuleOverride> snapshot() {
        return Map.copyOf(overrides);
    }
}
