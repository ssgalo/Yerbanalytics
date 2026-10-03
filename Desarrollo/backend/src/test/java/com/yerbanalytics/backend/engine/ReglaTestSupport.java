package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.ValorParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ayudas para testear una regla suelta: una {@link Evaluacion} con los valores de fábrica (o con overrides). */
public final class ReglaTestSupport {

    private ReglaTestSupport() {}

    /** Valores de fábrica del catálogo real. */
    public static ParametrosVigentes fabrica() {
        return new CatalogoParametros(List.of()).fabricas();
    }

    /** Fábrica con los overrides dados (valor en formato canónico, p. ej. {@code "60"}). */
    public static ParametrosVigentes con(Map<DefinicionParametro, String> overrides) {
        CatalogoParametros catalogo = new CatalogoParametros(List.of());
        Map<String, ValorParametro> valores = new HashMap<>(catalogo.fabricas().valores());
        overrides.forEach((def, texto) -> valores.put(def.clave(), def.tipo().parsear(texto, def)));
        return ParametrosVigentes.de(valores);
    }

    public static Evaluacion ev(Rule rule) {
        return new Evaluacion(rule, fabrica());
    }

    public static Evaluacion ev(Rule rule, Map<DefinicionParametro, String> overrides) {
        return new Evaluacion(rule, con(overrides));
    }

    /** Evalúa la regla con los valores de fábrica y descarta la traza. */
    public static List<RuleAction> evaluar(Rule rule, RuleContext ctx) {
        return rule.evaluate(ctx, ev(rule));
    }
}
