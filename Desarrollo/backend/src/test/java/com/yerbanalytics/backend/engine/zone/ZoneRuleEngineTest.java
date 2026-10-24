package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ZoneRuleEngine · reglas por macro-zona")
class ZoneRuleEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final List<String> executed = new ArrayList<>();
    private ZoneActionExecutor executor;
    private ZoneTraceStore traces;
    private ManualLockRepository locks;
    private TestRuleOverrides overrides;
    private ZonaEntity zone;

    private static ZoneRule rule(String name, int priority, Function<ZoneRuleContext, List<RuleAction>> body) {
        return new ZoneRule() {
            @Override public int priority() { return priority; }
            @Override public String name() { return name; }
            @Override public String label() { return name; }
            @Override public RuleBranch branch() { return RuleBranch.INSUMO; }
            @Override public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
                ev.compararFijo("marca " + name, 1, Operador.EQ, 1);
                return body.apply(ctx);
            }
        };
    }

    @BeforeEach
    void setUp() {
        executor = mock(ZoneActionExecutor.class);
        doAnswer(i -> {
            List<RuleAction> actions = i.getArgument(0);
            actions.forEach(a -> executed.add(a.ruleName()));
            return null;
        }).when(executor).execute(any(), any());
        traces = new ZoneTraceStore();
        locks = mock(ManualLockRepository.class);
        overrides = new TestRuleOverrides();
        zone = new ZonaEntity();
        zone.setId("MZ-2");
    }

    private ZoneRuleEngine engine(ZoneRule... rules) {
        return new ZoneRuleEngine(new ArrayList<>(List.of(rules)), parameters(), executor, traces, locks, overrides);
    }

    private CatalogoParametrosService parameters() {
        CatalogoParametrosService parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        return parameters;
    }

    private ZoneRuleContext ctx() {
        return new ZoneRuleContext(zone, List.of(), NOW, Map.of(), Map.of(), false, Set.of(),
                com.yerbanalytics.backend.engine.overrides.RuleOverrides.NONE);
    }

    private static RuleAction noop(String rule) {
        return RuleAction.noopInfo(rule, "ok");
    }

    @Test
    void correnPorPrioridadYCadaReglaEjecutaSusAccionesAntesDeLaSiguiente() {
        ZoneRule second = rule("Segunda", 23, c -> {
            executed.add("evalúa Segunda");
            return List.of(noop("Segunda"));
        });
        ZoneRule first = rule("Primera", 22, c -> {
            executed.add("evalúa Primera");
            return List.of(noop("Primera"));
        });

        engine(second, first).evaluate(ctx());

        // La segunda ve el efecto de la primera: la ejecuta antes de evaluarse.
        assertThat(executed).containsExactly("evalúa Primera", "Primera", "evalúa Segunda", "Segunda");
    }

    @Test
    void laTrazaTraeTodasLasReglasConSusComparacionesYAcciones() {
        ZoneRuleEngine engine = engine(rule("Primera", 22, c -> List.of(noop("Primera"))));

        List<TrazaRegla> trace = engine.evaluate(ctx());

        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).ruleId()).isEqualTo("Primera");
        assertThat(trace.get(0).estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(trace.get(0).rama()).isEqualTo(RuleBranch.INSUMO);
        assertThat(trace.get(0).comparaciones()).hasSize(1);
        assertThat(trace.get(0).acciones()).extracting(a -> a.tipo()).containsExactly(ActionType.NOOP_INFO);
        assertThat(traces.latest("MZ-2")).contains(trace);
    }

    @Test
    void unaReglaApagadaConUnOverrideNoCorreYQuedaEnLaTraza() {
        overrides.turnOff("Primera");
        ZoneRule first = rule("Primera", 22, c -> {
            throw new AssertionError("no debía correr");
        });
        ZoneRule second = rule("Segunda", 23, c -> List.of(noop("Segunda")));

        List<TrazaRegla> trace = engine(first, second).evaluate(ctx());

        assertThat(trace).extracting(TrazaRegla::estado)
                .containsExactly(EstadoRegla.OMITIDA_DESHABILITADA, EstadoRegla.EVALUADA);
        assertThat(executed).containsExactly("Segunda");
    }

    @Test
    void siUnaReglaLanzaLaTrazaParcialQuedaGuardadaYLaExcepcionSePropaga() {
        ZoneRule first = rule("Primera", 22, c -> {
            throw new IllegalStateException("boom");
        });
        ZoneRule second = rule("Segunda", 23, c -> List.of(noop("Segunda")));

        assertThatThrownBy(() -> engine(first, second).evaluate(ctx()))
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");

        List<TrazaRegla> saved = traces.latest("MZ-2").orElseThrow();
        assertThat(saved).extracting(TrazaRegla::estado).containsExactly(EstadoRegla.ERROR, EstadoRegla.NO_ALCANZADA);
        assertThat(saved.get(0).error()).contains("IllegalStateException: boom");
        assertThat(saved.get(1).bloqueadaPor()).isEqualTo("Primera");
        assertThat(executed).isEmpty();
    }

    @Test
    void losOverridesLosPoneElMotorYNoQuienArmaElContexto() {
        ZoneRule probe = rule("Probe", 22, c -> {
            executed.add("bypass=" + c.isBypassed("Probe", "k") + " cooldown=" + c.cooldownOverrideMinutes("Probe", "k"));
            return List.of();
        });
        overrides.put("Probe", new RuleOverride(Map.of("k", 0.0), Set.of("k")));

        engine(probe).evaluate(ctx());

        assertThat(executed).containsExactly("bypass=true cooldown=Optional[0.0]");
    }

    @Test
    void elContextoDeLaIngestaIncluyeLosBloqueosVigentes() {
        ManualLockEntity zoneLock = new ManualLockEntity();
        zoneLock.setZonaId("MZ-2");
        ManualLockEntity sectorLock = new ManualLockEntity();
        sectorLock.setSectorId("MZ-2-005");
        ManualLockEntity otherZoneLock = new ManualLockEntity();
        otherZoneLock.setZonaId("MZ-5");
        when(locks.findByActiveTrue()).thenReturn(List.of(zoneLock, sectorLock, otherZoneLock));
        List<ZoneRuleContext> seen = new ArrayList<>();
        ZoneRule probe = rule("Probe", 22, c -> {
            seen.add(c);
            return List.of();
        });
        SectorEntity sector = new SectorEntity();
        sector.setId("MZ-2-005");

        engine(probe).evaluate(zone, List.of(sector), NOW, Map.of("MZ-2-005", HardwareCoverage.COMPLETE), Map.of(), null,
                MallaZona.OPERATIVA);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).zoneLocked()).isTrue();
        assertThat(seen.get(0).lockedSectors()).containsExactly("MZ-2-005");
        assertThat(seen.get(0).now()).isEqualTo(NOW);
    }

    @Test
    void unBloqueoSoloDeOtraZonaNoBloqueaEstaZona() {
        ManualLockEntity otherZoneLock = new ManualLockEntity();
        otherZoneLock.setZonaId("MZ-5");
        when(locks.findByActiveTrue()).thenReturn(List.of(otherZoneLock));
        List<ZoneRuleContext> seen = new ArrayList<>();

        engine(rule("Probe", 22, c -> {
            seen.add(c);
            return List.of();
        })).evaluate(zone, List.of(), NOW, Map.of(), Map.of(), null, MallaZona.OPERATIVA);

        assertThat(seen.get(0).zoneLocked()).isFalse();
    }

    @Test
    void exponeLasReglasOrdenadasPorPrioridadParaElEsquemaYElCatalogo() {
        ZoneRuleEngine engine = engine(rule("B", 23, c -> List.of()), rule("A", 22, c -> List.of()));

        assertThat(engine.getRules()).extracting(ZoneRule::name).containsExactly("A", "B");
    }
    @Test
    void unAbortMediasombraCortaLasReglasQueSiguenDeEsaRama() {
        ZoneRule guarda = mediasombra("Guarda", 13,
                c -> List.of(RuleAction.of(ActionType.ABORT_MEDIASOMBRA, "Guarda", "falla")));
        ZoneRule mueve = mediasombra("Mueve", 14, c -> List.of(noop("Mueve")));
        ZoneRule otra = rule("Otra", 22, c -> List.of(noop("Otra")));

        List<TrazaRegla> trace = engine(otra, mueve, guarda).evaluate(ctx());

        assertThat(trace).extracting(TrazaRegla::ruleId).containsExactly("Guarda", "Mueve", "Otra");
        assertThat(trace.get(1).estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(trace.get(1).bloqueadaPor()).isEqualTo("Guarda");
        assertThat(trace.get(2).estado()).as("las otras ramas siguen").isEqualTo(EstadoRegla.EVALUADA);
        assertThat(executed).containsExactly("Guarda", "Otra");
    }

    private static ZoneRule mediasombra(String name, int priority, Function<ZoneRuleContext, List<RuleAction>> body) {
        ZoneRule base = rule(name, priority, body);
        return new ZoneRule() {
            @Override public int priority() { return base.priority(); }
            @Override public String name() { return base.name(); }
            @Override public String label() { return base.label(); }
            @Override public RuleBranch branch() { return RuleBranch.MEDIASOMBRA; }
            @Override public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) { return base.evaluate(ctx, ev); }
        };
    }
}
