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

    /**
     * Fábrica con los umbrales de v2 fijados a mano (umbral de riego 45 %, lluvia 70 %): hasta la
     * conmutación (10.5) las fábricas de esas dos claves siguen en 42 / 60 porque las reglas viejas las leen.
     * En 10.5 este método se reemplaza por {@link #ev(Rule)}.
     */
    public static ParametrosVigentes vigentesV2() {
        return con(Map.of(com.yerbanalytics.backend.engine.parametros.ParametrosRiego.UMBRAL_HUMEDAD, "45",
                com.yerbanalytics.backend.engine.parametros.ParametrosRiego.LLUVIA_PROBABILIDAD, "70"));
    }

    public static Evaluacion evV2(Rule rule) {
        return evV2(rule, Map.of());
    }

    public static Evaluacion evV2(Rule rule, Map<DefinicionParametro, String> overrides) {
        Map<DefinicionParametro, String> todos = new HashMap<>();
        todos.put(com.yerbanalytics.backend.engine.parametros.ParametrosRiego.UMBRAL_HUMEDAD, "45");
        todos.put(com.yerbanalytics.backend.engine.parametros.ParametrosRiego.LLUVIA_PROBABILIDAD, "70");
        todos.putAll(overrides);
        return new Evaluacion(rule, con(todos));
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
