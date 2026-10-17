package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.RuleBranch;

import java.util.Set;

/** Pieza de decisión de prueba que no es una {@code Rule}: como una regla por macro-zona o un servicio. */
public record FakeControllable(String name, String label, RuleBranch branch, int priority,
                               Set<String> cooldownKeys, Set<String> bypassKeys) implements DemoControllable {

    public static FakeControllable of(String name, int priority) {
        return new FakeControllable(name, "Etiqueta " + name, RuleBranch.MEDIASOMBRA, priority,
                Set.of("interval"), Set.of("window"));
    }
}
