package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.dto.UltimoCambioReglaDto;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.overrides.RuleState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Arma, para la API, la lista de todo lo que se puede prender o apagar: las reglas por sector y de macro-zona y también
 * los servicios que deciden por su cuenta. Es la vista del catálogo "por pieza": qué es, a qué objetivo sirve, si corre,
 * si es protección y quién la cambió. Los parámetros los referencia por clave; sus valores son de
 * {@link CatalogoParametrosService}.
 */
@Component
public class ReglasCatalogo {

    private final DemoControllableRegistry registry;
    private final OperationalRuleStates states;
    private final RuleOverridesStore demo;

    public ReglasCatalogo(DemoControllableRegistry registry, OperationalRuleStates states, RuleOverridesStore demo) {
        this.registry = registry;
        this.states = states;
        this.demo = demo;
    }

    /** Todas las piezas, por prioridad y luego por nombre. */
    public List<ReglaCatalogoDto> reglas() {
        return registry.all().stream().map(p -> regla(p, states.state(p.name()).orElse(null))).toList();
    }

    /** Una pieza con el estado vigente. */
    public Optional<ReglaCatalogoDto> regla(String name) {
        return registry.find(name).map(p -> regla(p, states.state(p.name()).orElse(null)));
    }

    /**
     * Una pieza con un estado dado ({@code null} = nunca cambiada). Lo usa quien acaba de escribirlo, antes del commit,
     * cuando el cache todavía no lo refleja.
     */
    public ReglaCatalogoDto regla(DemoControllable pieza, RuleState estado) {
        String name = pieza.name();
        boolean activaPorDefecto = states.activeByDefault(name);
        List<String> parametros = pieza instanceof ConsumidorParametros c
                ? c.parametros().stream().map(DefinicionParametro::clave).toList()
                : List.of();
        return new ReglaCatalogoDto(
                name,
                pieza.label(),
                pieza.code(),
                pieza.branch().name(),
                pieza.objective().name(),
                pieza.effect().name(),
                pieza.priority(),
                parametros,
                estado != null ? estado.active() : activaPorDefecto,
                activaPorDefecto,
                demo.explicitEnabled(name).map(enabled -> !enabled).orElse(false),
                pieza.protection().orElse(null),
                estado != null ? new UltimoCambioReglaDto(estado.reason(), estado.updatedBy(), estado.updatedTs()) : null);
    }
}
