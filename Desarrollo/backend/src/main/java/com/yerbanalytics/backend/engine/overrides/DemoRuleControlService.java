package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Casos de uso del control de demo: listar las piezas con su override, cambiarlo validando que las claves existan
 * y poner todos los cooldowns en cero. Escribe sólo cooldowns y bypass en el {@link RuleOverridesStore}; el controlador
 * HTTP que lo usa existe únicamente con el modo demo. Nunca escribe el estado operativo (el encendido): eso es del
 * vivero, desde Motor de reglas.
 */
@Component
public class DemoRuleControlService {

    private final DemoControllableRegistry registry;
    private final RuleOverridesStore store;
    private final EffectiveRuleOverrides effective;

    public DemoRuleControlService(DemoControllableRegistry registry, RuleOverridesStore store,
                                  EffectiveRuleOverrides effective) {
        this.registry = registry;
        this.store = store;
        this.effective = effective;
    }

    public List<ControllableState> list() {
        return registry.all().stream().map(this::stateOf).toList();
    }

    /**
     * Aplica sólo los campos presentes ({@code null} conserva el valor actual). Todo o nada: si una clave no está
     * declarada por la pieza no se guarda nada.
     *
     * @throws UnknownControllableException si el nombre no existe
     * @throws InvalidOverrideKeyException  si alguna clave de cooldown o de bypass no la declara la pieza
     */
    public ControllableState update(String name, Map<String, Double> cooldownMinutes, Set<String> bypassed) {
        DemoControllable controllable = registry.find(name).orElseThrow(() -> new UnknownControllableException(name));
        if (cooldownMinutes != null) {
            requireDeclared(name, "cooldowns", cooldownMinutes.keySet(), controllable.cooldownKeys());
        }
        if (bypassed != null) {
            requireDeclared(name, "bypass", bypassed, controllable.bypassKeys());
        }

        RuleOverride current = store.get(name);
        store.put(name, new RuleOverride(
                cooldownMinutes != null ? cooldownMinutes : current.cooldownMinutes(),
                bypassed != null ? bypassed : current.bypassed()));
        return stateOf(controllable);
    }

    /** Pone en 0 todos los cooldowns declarados, de todas las piezas, sin tocar el resto de cada override. */
    public List<ControllableState> zeroCooldowns() {
        for (DemoControllable controllable : registry.all()) {
            if (controllable.cooldownKeys().isEmpty()) {
                continue;
            }
            RuleOverride current = store.get(controllable.name());
            Map<String, Double> zeroed = new HashMap<>(current.cooldownMinutes());
            controllable.cooldownKeys().forEach(key -> zeroed.put(key, 0.0));
            store.put(controllable.name(), new RuleOverride(zeroed, current.bypassed()));
        }
        return list();
    }

    private ControllableState stateOf(DemoControllable controllable) {
        String name = controllable.name();
        return new ControllableState(controllable, store.get(name), effective.isEnabled(name));
    }

    private static void requireDeclared(String name, String kind, Set<String> given, Set<String> declared) {
        Set<String> unknown = new TreeSet<>(given);
        unknown.removeAll(declared);
        if (!unknown.isEmpty()) {
            throw new InvalidOverrideKeyException(name, kind, unknown, new TreeSet<>(declared));
        }
    }
}
