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
 * <p>No decide por sí sola si una pieza corre: eso lo resuelve {@link EffectiveRuleOverrides}, donde el override explícito
 * de esta capa gana sobre el estado operativo ({@link OperationalRuleStates}), que es donde viven las reglas retiradas.
 * Un override sin interruptor explícito ({@link RuleOverride#explicitEnabled()} vacío) deja decidir al operativo.
 */
@Component
public class RuleOverridesStore {

    private final Map<String, RuleOverride> overrides = new ConcurrentHashMap<>();

    /** El interruptor explícito de demo de una pieza; vacío si nunca se tocó (o sólo se tocaron cooldowns o bypass). */
    public Optional<Boolean> explicitEnabled(String ruleName) {
        return get(ruleName).explicitEnabled();
    }

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

    /** Borra todos los overrides: vuelve a regir el estado operativo y la configuración de cada pieza. */
    public void clear() {
        overrides.clear();
    }

    /** Snapshot of the rules that have an override. */
    public Map<String, RuleOverride> snapshot() {
        return Map.copyOf(overrides);
    }
}
