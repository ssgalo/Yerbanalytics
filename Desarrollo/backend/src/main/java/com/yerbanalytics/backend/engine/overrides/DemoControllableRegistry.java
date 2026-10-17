package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Única fuente de las piezas controlables desde el simulador: reúne todos los beans {@link DemoControllable},
 * estén o no en la cadena de reglas por sector. Los nombres son únicos porque son la clave del override.
 */
@Component
public class DemoControllableRegistry {

    private static final Comparator<DemoControllable> ORDEN =
            Comparator.comparingInt(DemoControllable::priority).thenComparing(DemoControllable::name);

    private final List<DemoControllable> controllables;

    public DemoControllableRegistry(List<DemoControllable> controllables) {
        Set<String> vistos = new HashSet<>();
        for (DemoControllable c : controllables) {
            if (!vistos.add(c.name())) {
                throw new IllegalStateException("Hay dos piezas controlables con el nombre '" + c.name() + "'.");
            }
        }
        this.controllables = controllables.stream().sorted(ORDEN).toList();
    }

    /** Todas las piezas, por prioridad y luego por nombre. */
    public List<DemoControllable> all() {
        return controllables;
    }

    public Optional<DemoControllable> find(String name) {
        return controllables.stream().filter(c -> c.name().equals(name)).findFirst();
    }
}
