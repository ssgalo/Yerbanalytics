package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tarea 6.3: quien lee un parámetro sin ser una regla (el despacho de riego) también figura en {@code usadoPor}. */
@DisplayName("ConsumidorParametros")
class ConsumidorParametrosTest {

    private static final DefinicionParametro SIMULTANEOS = ParametrosRiego.SECTORES_SIMULTANEOS;

    private static ConsumidorParametros consumidor(String nombre, DefinicionParametro... params) {
        return new ConsumidorParametros() {
            @Override public String name() { return nombre; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
        };
    }

    private static Rule regla(String nombre, int prioridad, DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return prioridad; }
            @Override public String name() { return nombre; }
            @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) { return List.of(); }
        };
    }

    @Test
    void ruleEsUnConsumidorDeParametros() {
        assertThat(ConsumidorParametros.class).isAssignableFrom(Rule.class);
    }

    @Test
    void unConsumidorQueNoEsReglaApareceEnUsadoPorYNoEnReglas() {
        ConsumidorParametros despacho = consumidor("DespachoRiego", SIMULTANEOS);

        CatalogoParametros cat = new CatalogoParametros(
                CatalogoParametros.definicionesReales(), CatalogoParametros.restriccionesReales(), List.of(despacho));

        assertThat(cat.usadoPor("riego.sectores-simultaneos")).containsExactly("DespachoRiego");
        assertThat(cat.reglas()).isEmpty();
    }

    @Test
    void primeroLasReglasPorPrioridadYDespuesLosOtrosConsumidores() {
        ConsumidorParametros despacho = consumidor("DespachoRiego", SIMULTANEOS);
        Rule tarde = regla("Tarde", 9, SIMULTANEOS);
        Rule temprano = regla("Temprano", 2, SIMULTANEOS);

        CatalogoParametros cat = new CatalogoParametros(
                CatalogoParametros.definicionesReales(), CatalogoParametros.restriccionesReales(),
                List.of(despacho, tarde, temprano));

        assertThat(cat.usadoPor("riego.sectores-simultaneos")).containsExactly("Temprano", "Tarde", "DespachoRiego");
        assertThat(cat.reglas()).containsExactly(temprano, tarde);
    }

    @Test
    void unConsumidorQueDeclaraUnaClaveInexistenteImpideConstruirElCatalogo() {
        DefinicionParametro fantasma = DefinicionDePrueba.entero("riego.fantasma", "1", 0.0, 5.0);
        ConsumidorParametros malo = consumidor("DespachoRiego", fantasma);

        assertThatThrownBy(() -> new CatalogoParametros(
                CatalogoParametros.definicionesReales(), CatalogoParametros.restriccionesReales(), List.of(malo)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DespachoRiego")
                .hasMessageContaining("riego.fantasma");
    }
}
