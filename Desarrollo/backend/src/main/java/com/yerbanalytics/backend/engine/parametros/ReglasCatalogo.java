package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.dto.UltimoCambioReglaDto;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
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

    public ReglasCatalogo(DemoControllableRegistry registry, OperationalRuleStates states) {
        this.registry = registry;
        this.states = states;
    }

    /** Todas las piezas, por prioridad y luego por nombre. */
    public List<ReglaCatalogoDto> reglas() {
        return registry.all().stream().map(p -> regla(p, states.row(p.name()).orElse(null))).toList();
    }

    /** Una pieza con el estado vigente. */
    public Optional<ReglaCatalogoDto> regla(String name) {
        return registry.find(name).map(p -> regla(p, states.row(p.name()).orElse(null)));
    }

    /**
     * Una pieza con una fila dada ({@code null} = sin fila). Lo usa quien acaba de escribirla, antes del commit, cuando
     * el cache todavía no la refleja.
     */
    public ReglaCatalogoDto regla(DemoControllable pieza, RuleState fila) {
        String name = pieza.name();
        boolean activaPorDefecto = states.activeByDefault(name);
        List<String> parametros = pieza instanceof ConsumidorParametros c
                ? c.parametros().stream().map(DefinicionParametro::clave).toList()
                : List.of();
        boolean decidida = fila != null && fila.switchDecided();
        boolean eliminada = fila != null && fila.deleted();
        boolean activa = !eliminada && (decidida ? fila.active() : activaPorDefecto);
        return new ReglaCatalogoDto(
                name,
                fila != null && fila.label() != null ? fila.label() : pieza.label(),
                pieza.label(),
                pieza.code(),
                pieza.branch().name(),
                pieza.objective().name(),
                pieza.effect().name(),
                pieza.priority(),
                parametros,
                activa,
                activaPorDefecto,
                pieza.protection().orElse(null),
                decidida ? new UltimoCambioReglaDto(fila.reason(), fila.updatedBy(), fila.updatedTs()) : null,
                eliminada);
    }
}
