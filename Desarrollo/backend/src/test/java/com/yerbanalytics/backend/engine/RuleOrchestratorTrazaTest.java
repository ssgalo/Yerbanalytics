package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ValorParametro;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tarea 3.4: el orquestador arma la traza por regla sin cambiar las acciones que devuelve. */
@DisplayName("RuleOrchestrator - traza")
class RuleOrchestratorTrazaTest {

    /** Regla falsa: emite acciones fijas, o lo que decida el cuerpo si usa la {@code Evaluacion}. */
    private static final class ReglaFalsa implements Rule {
        private final String name;
        private final int priority;
        private final RuleBranch branch;
        private final List<DefinicionParametro> parametros;
        private final BiFunction<RuleContext, com.yerbanalytics.backend.engine.traza.Evaluacion, List<RuleAction>> cuerpo;
        final List<String> llamadas = new ArrayList<>();

        ReglaFalsa(String name, int priority, RuleBranch branch, ActionType... tipos) {
            this(name, priority, branch, List.of(), (ctx, ev) -> java.util.Arrays.stream(tipos)
                    .map(t -> RuleAction.of(t, name, "motivo " + t)).toList());
        }

        ReglaFalsa(String name, int priority, RuleBranch branch, List<DefinicionParametro> parametros,
                   BiFunction<RuleContext, com.yerbanalytics.backend.engine.traza.Evaluacion, List<RuleAction>> cuerpo) {
            this.name = name;
            this.priority = priority;
            this.branch = branch;
            this.parametros = parametros;
            this.cuerpo = cuerpo;
        }

        @Override public int priority() { return priority; }
        @Override public String name() { return name; }
        @Override public RuleBranch branch() { return branch; }
        @Override public List<DefinicionParametro> parametros() { return parametros; }

        @Override
        public List<RuleAction> evaluate(RuleContext ctx) {
            llamadas.add("viejo");
            return cuerpo.apply(ctx, null);
        }

        @Override
        public List<RuleAction> evaluate(RuleContext ctx, com.yerbanalytics.backend.engine.traza.Evaluacion ev) {
            llamadas.add("nuevo");
            return cuerpo.apply(ctx, ev);
        }
    }

    private CatalogoParametrosService parametros;
    private RuleContext ctx;

    @BeforeEach
    void setUp() {
        parametros = mock(CatalogoParametrosService.class);
        when(parametros.vigentes()).thenReturn(ParametrosVigentes.de(Map.of(
                ParametrosRiego.UMBRAL_HUMEDAD.clave(), new ValorParametro.Numero(42),
                ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA.clave(), new ValorParametro.Numero(30))));
        ctx = RuleContextTestFactory.basico(
                RuleContextTestFactory.sectorBasico("MZ-2-006"), RuleContextTestFactory.zonaBasica("MZ-2"));
    }

    private ResultadoEvaluacion evaluar(Rule... reglas) {
        // Se pasan desordenadas a propósito: el orquestador las ordena por prioridad.
        List<Rule> desordenadas = new ArrayList<>(List.of(reglas));
        java.util.Collections.reverse(desordenadas);
        return new RuleOrchestrator(desordenadas, parametros).evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
    }

    private static TrazaRegla de(ResultadoEvaluacion r, String regla) {
        return r.traza().reglas().stream().filter(t -> t.ruleId().equals(regla)).findFirst().orElseThrow();
    }

    @Test
    void ramaDeRiegoBloqueada_omiteLasSiguientesDeEsaRamaConLaReglaQueBloqueo() {
        Rule lluvia = new ReglaFalsa("Lluvia", 5, RuleBranch.RIEGO, ActionType.POSTPONE_RIEGO);
        Rule volumen = new ReglaFalsa("Volumen", 6, RuleBranch.RIEGO, ActionType.NOOP_INFO);
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, ActionType.ACTIVAR_VALVULA);
        Rule sombra = new ReglaFalsa("Sombra", 20, RuleBranch.MEDIASOMBRA, ActionType.MOVER_MEDIASOMBRA);

        ResultadoEvaluacion r = evaluar(lluvia, volumen, riego, sombra);

        assertThat(de(r, "Lluvia").estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(de(r, "Volumen").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(de(r, "Volumen").bloqueadaPor()).isEqualTo("Lluvia");
        assertThat(de(r, "Riego").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(de(r, "Riego").bloqueadaPor()).isEqualTo("Lluvia");
        assertThat(de(r, "Sombra").estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(de(r, "Sombra").bloqueadaPor()).isNull();
        // Las omitidas no se evaluaron: sin acciones ni comparaciones.
        assertThat(de(r, "Riego").acciones()).isEmpty();
        assertThat(((ReglaFalsa) riego).llamadas).isEmpty();
    }

    @Test
    void ramaDeInsumoBloqueada_omiteLasSiguientesDeInsumo() {
        Rule dosis = new ReglaFalsa("Dosis", 7, RuleBranch.INSUMO, ActionType.ABORT_INSUMO);
        Rule abasto = new ReglaFalsa("Abasto", 12, RuleBranch.INSUMO, ActionType.ACTIVAR_BOMBA);
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, ActionType.ACTIVAR_VALVULA);

        ResultadoEvaluacion r = evaluar(dosis, abasto, riego);

        assertThat(de(r, "Abasto").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(de(r, "Abasto").bloqueadaPor()).isEqualTo("Dosis");
        assertThat(de(r, "Riego").estado()).isEqualTo(EstadoRegla.EVALUADA);
    }

    @Test
    void abortAll_dejaElRestoNoAlcanzada() {
        Rule bloqueo = new ReglaFalsa("Bloqueo", 1, RuleBranch.GLOBAL, ActionType.ABORT_ALL);
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, ActionType.ACTIVAR_VALVULA);
        Rule sombra = new ReglaFalsa("Sombra", 20, RuleBranch.MEDIASOMBRA, ActionType.MOVER_MEDIASOMBRA);

        ResultadoEvaluacion r = evaluar(bloqueo, riego, sombra);

        assertThat(de(r, "Bloqueo").estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(de(r, "Riego").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
        assertThat(de(r, "Sombra").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
        assertThat(((ReglaFalsa) sombra).llamadas).isEmpty();
    }

    @Test
    void lasAccionesDevueltasSonLasMismasQueAntesDeLaTraza() {
        Rule lluvia = new ReglaFalsa("Lluvia", 5, RuleBranch.RIEGO, ActionType.POSTPONE_RIEGO);
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, ActionType.ACTIVAR_VALVULA);
        Rule sombra = new ReglaFalsa("Sombra", 20, RuleBranch.MEDIASOMBRA, ActionType.NOOP_INFO, ActionType.MOVER_MEDIASOMBRA);

        ResultadoEvaluacion r = evaluar(lluvia, riego, sombra);

        assertThat(r.acciones()).extracting(RuleAction::type)
                .containsExactly(ActionType.POSTPONE_RIEGO, ActionType.NOOP_INFO, ActionType.MOVER_MEDIASOMBRA);
        assertThat(r.acciones()).extracting(RuleAction::ruleName).containsExactly("Lluvia", "Sombra", "Sombra");
    }

    @Test
    void trazaIncluyeTodasLasReglasEnOrdenDePrioridadYSusAcciones() {
        Rule bloqueo = new ReglaFalsa("Bloqueo", 1, RuleBranch.GLOBAL, ActionType.NOOP_INFO);
        Rule sombra = new ReglaFalsa("Sombra", 20, RuleBranch.MEDIASOMBRA, ActionType.MOVER_MEDIASOMBRA);
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, ActionType.ACTIVAR_VALVULA);

        ResultadoEvaluacion r = evaluar(sombra, riego, bloqueo);

        assertThat(r.traza().reglas()).extracting(TrazaRegla::ruleId).containsExactly("Bloqueo", "Riego", "Sombra");
        assertThat(r.traza().reglas()).extracting(TrazaRegla::prioridad).containsExactly(1, 10, 20);
        assertThat(r.traza().reglas()).extracting(TrazaRegla::rama)
                .containsExactly(RuleBranch.GLOBAL, RuleBranch.RIEGO, RuleBranch.MEDIASOMBRA);
        assertThat(de(r, "Riego").acciones()).hasSize(1);
        assertThat(de(r, "Riego").acciones().get(0).tipo()).isEqualTo(ActionType.ACTIVAR_VALVULA);
        assertThat(de(r, "Riego").acciones().get(0).motivo()).isEqualTo("motivo ACTIVAR_VALVULA");
    }

    @Test
    void trazaLlevaSectorZonaOrigenYMomentoDelContexto() {
        ResultadoEvaluacion r = evaluar(new ReglaFalsa("Riego", 10, RuleBranch.RIEGO));

        assertThat(r.traza().sectorId()).isEqualTo("MZ-2-006");
        assertThat(r.traza().zonaId()).isEqualTo("MZ-2");
        assertThat(r.traza().origen()).isEqualTo(OrigenEvaluacion.TELEMETRIA);
        assertThat(r.traza().ts()).isEqualTo(ctx.now());
        assertThat(r.traza().parametrosHash()).isNotBlank();
    }

    @Test
    void elOrigenDeLaTrazaEsElQueSePide() {
        ResultadoEvaluacion r = new RuleOrchestrator(List.of(new ReglaFalsa("Riego", 10, RuleBranch.RIEGO)), parametros)
                .evaluate(ctx, OrigenEvaluacion.BARRIDO);

        assertThat(r.traza().origen()).isEqualTo(OrigenEvaluacion.BARRIDO);
    }

    @Test
    void siLaZonaEsNulaLaTrazaQuedaSinZona() {
        RuleContext sinZona = new RuleContext(ctx.sector(), null, ctx.specs(), ctx.metrics(), ctx.config(),
                ctx.finalStatus(), ctx.now(), false, null, false);

        ResultadoEvaluacion r = new RuleOrchestrator(List.of(new ReglaFalsa("Riego", 10, RuleBranch.RIEGO)), parametros)
                .evaluate(sinZona, OrigenEvaluacion.BARRIDO);

        assertThat(r.traza().zonaId()).isNull();
    }

    @Test
    void tomaLosValoresVigentesUnaSolaVezPorSector() {
        evaluar(new ReglaFalsa("A", 1, RuleBranch.GLOBAL), new ReglaFalsa("B", 2, RuleBranch.RIEGO),
                new ReglaFalsa("C", 3, RuleBranch.MEDIASOMBRA));

        verify(parametros, times(1)).vigentes();
    }

    @Test
    void lasComparacionesQueRegistraLaReglaQuedanEnSuTraza() {
        Rule riego = new ReglaFalsa("Riego", 10, RuleBranch.RIEGO, List.of(ParametrosRiego.UMBRAL_HUMEDAD),
                (c, ev) -> ev.comparar("Humedad de sustrato", 38.0, Operador.LT, ParametrosRiego.UMBRAL_HUMEDAD)
                        ? List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "Riego", "regar"))
                        : List.of());

        ResultadoEvaluacion r = evaluar(riego);

        assertThat(r.acciones()).hasSize(1);
        assertThat(de(r, "Riego").comparaciones()).hasSize(1);
        assertThat(de(r, "Riego").comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        assertThat(de(r, "Riego").comparaciones().get(0).umbral()).isEqualTo(42.0);
    }

    @Test
    void unaReglaQueLeeUnParametroNoDeclaradoReventaElCiclo() {
        Rule mala = new ReglaFalsa("Mala", 10, RuleBranch.RIEGO, List.of(),
                (c, ev) -> { ev.numero(ParametrosRiego.UMBRAL_HUMEDAD); return List.of(); });

        assertThatThrownBy(() -> evaluar(mala)).isInstanceOf(ParametroNoDeclaradoException.class);
    }

    @Test
    void laFirmaPuenteDelegaEnEvaluateViejoParaLasReglasSinMigrar() {
        // Una regla que sólo implementa evaluate(ctx): el default de evaluate(ctx, ev) la llama.
        Rule sinMigrar = new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "SinMigrar"; }
            @Override public List<RuleAction> evaluate(RuleContext c) {
                return List.of(RuleAction.of(ActionType.NOOP_INFO, "SinMigrar", "ok"));
            }
        };

        ResultadoEvaluacion r = evaluar(sinMigrar);

        assertThat(r.acciones()).hasSize(1);
        assertThat(de(r, "SinMigrar").estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(de(r, "SinMigrar").comparaciones()).isEmpty();
    }
}
