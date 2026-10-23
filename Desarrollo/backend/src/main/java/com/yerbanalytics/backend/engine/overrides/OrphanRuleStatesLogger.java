package com.yerbanalytics.backend.engine.overrides;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Al arrancar, avisa de los estados guardados de reglas que ya no están en el código. */
@Component
public class OrphanRuleStatesLogger {

    private final OperationalRuleStates states;
    private final DemoControllableRegistry registry;

    public OrphanRuleStatesLogger(OperationalRuleStates states, DemoControllableRegistry registry) {
        this.states = states;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        states.logOrphans(registry.all().stream().map(DemoControllable::name).toList());
    }
}
