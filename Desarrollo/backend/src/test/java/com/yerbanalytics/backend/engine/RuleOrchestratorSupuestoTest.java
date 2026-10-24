package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** O-01…O-03: la regla que usa el pronóstico declara su supuesto seguro y la traza lo muestra cuando falta. */
@DisplayName("RuleOrchestrator - supuesto sin pronóstico")
class RuleOrchestratorSupuestoTest {

    private static final class ReglaDePronostico implements Rule {
        private final String supuesto;

        ReglaDePronostico(String supuesto) {
            this.supuesto = supuesto;
        }

        @Override public int priority() { return 10; }
        @Override public String name() { return "ReglaDePronostico"; }
        @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
        @Override public String supuestoSinPronostico() { return supuesto; }

        @Override
        public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
            return List.of(RuleAction.noopInfo(name(), "nada"));
        }
    }

    private final TrazaEvaluacionStore store = new TrazaEvaluacionStore();
    private CatalogoParametrosService catalogo;

    @BeforeEach
    void setUp() {
        catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
    }

    private TrazaRegla trazaDe(Rule regla, WeatherForecast forecast) {
        var sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        var zona = RuleContextTestFactory.zonaBasica("MZ-1");
        sector.setZona(zona);
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok",
                Instant.now(), forecast, false);
        var resultado = new RuleOrchestrator(List.of(regla), catalogo, store).evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
        return resultado.traza().reglas().get(0);
    }

    private static WeatherForecast pronostico() {
        return new WeatherForecast(10, 5, Instant.now(), 22.0, "Despejado", 60.0, List.of());
    }

    @Test
    void sinPronosticoLaTrazaDiceQueSupuestoSeUso() {
        TrazaRegla traza = trazaDe(new ReglaDePronostico("no llueve: no se pospone el riego"), null);

        assertThat(traza.supuesto()).isEqualTo("Sin pronóstico: no llueve: no se pospone el riego");
    }

    @Test
    void conPronosticoNoHaySupuesto() {
        assertThat(trazaDe(new ReglaDePronostico("no llueve"), pronostico()).supuesto()).isNull();
    }

    @Test
    void unaReglaQueNoUsaPronosticoNuncaTieneSupuesto() {
        assertThat(trazaDe(new ReglaDePronostico(""), null).supuesto()).isNull();
    }

    @Test
    void r03DeclaraQueSinPronosticoSeAsumeQueNoLlueve() {
        assertThat(new PosponerPorLluviaRule().supuestoSinPronostico()).containsIgnoringCase("no llueve");
    }

    @Test
    void porDefectoUnaReglaNoDeclaraSupuesto() {
        assertThat(new ReglaDePronostico("").supuestoSinPronostico()).isEmpty();
        Rule sinPronostico = new Rule() {
            @Override public int priority() { return 1; }
            @Override public String name() { return "X"; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) { return List.of(); }
        };
        assertThat(sinPronostico.supuestoSinPronostico()).isEmpty();
    }
}
