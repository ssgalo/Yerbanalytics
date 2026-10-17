package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.engine.rules.ShadingRule;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * reglas_v2 S-01 (add-bloqueo-manual, D-2): con un bloqueo vigente la mediasombra queda quieta. Hoy la mediasombra se
 * evalúa por sector con {@link ShadingRule}, y el bloqueo de una macro-zona llega a cada uno de sus sectores como
 * {@code bloqueoManualActivo}. Cuando exista la evaluación por macro-zona ({@code add-mediasombra-v2}), M-01 y M-02 deben
 * consultar el bloqueo de la zona: este test fija el comportamiento que hay que conservar.
 */
@DisplayName("El bloqueo manual deja quieta la mediasombra")
class ManualLockShadingTest {

    private RuleOrchestrator orchestrator;
    private CatalogoParametrosService catalogo;
    private ShadingRule shading;

    @BeforeEach
    void setUp() {
        catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        shading = new ShadingRule(mock(RustificacionEtapaRepository.class), "");
        orchestrator = orchestratorWith(RuleOverrides.NONE);
    }

    private RuleOrchestrator orchestratorWith(RuleOverrides overrides) {
        return new RuleOrchestrator(List.of(new ManualLockRule(mock(ManualLockRepository.class)), shading), catalogo,
                new TrazaEvaluacionStore(), overrides);
    }

    /** Lo que el simulador hace desde el panel de reglas: apagar la regla o puentear su guarda. */
    private static RuleOverrides simulatorOverrides(boolean lockRuleEnabled, boolean lockBypassed) {
        return new RuleOverrides() {
            @Override public boolean isEnabled(String ruleName) {
                return lockRuleEnabled || !ManualLockRule.NAME.equals(ruleName);
            }
            @Override public Optional<Double> cooldownMinutes(String ruleName, String key) {
                return Optional.empty();
            }
            @Override public boolean isBypassed(String ruleName, String key) {
                return lockBypassed && ManualLockRule.NAME.equals(ruleName) && ManualLockRule.BYPASS_LOCK.equals(key);
            }
        };
    }

    private ResultadoEvaluacion evaluateAtUvPeak(boolean locked) {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setActuadorShade(50);
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        Instant now = Instant.now();
        WeatherForecast uvPeak = new WeatherForecast(10.0, 9.5, now, 20.0, "Soleado", 40.0, List.of());
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", now, uvPeak, locked);
        return orchestrator.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
    }

    @Test
    @DisplayName("sin bloqueo, con UV máximo la mediasombra se mueve (el punto de comparación del test)")
    void withoutLockTheShadeMoves() {
        assertThat(evaluateAtUvPeak(false).acciones()).extracting(RuleAction::type)
                .contains(ActionType.MOVER_MEDIASOMBRA);
    }

    @Test
    @DisplayName("con bloqueo, la cadena se corta y la mediasombra no recibe ninguna orden")
    void withLockTheShadeStaysStill() {
        ResultadoEvaluacion result = evaluateAtUvPeak(true);

        assertThat(result.acciones()).extracting(RuleAction::type)
                .contains(ActionType.ABORT_ALL)
                .doesNotContain(ActionType.MOVER_MEDIASOMBRA);
        assertThat(result.traza().reglas())
                .filteredOn(t -> t.ruleId().equals("ShadingRule"))
                .singleElement()
                .satisfies(t -> assertThat(t.estado()).isNotEqualTo(EstadoRegla.EVALUADA));
    }

    @Test
    @DisplayName("apagada desde el simulador, la regla no corta nada y la traza dice 'deshabilitada'")
    void aSwitchedOffRuleDoesNotCut() {
        orchestrator = orchestratorWith(simulatorOverrides(false, false));

        ResultadoEvaluacion result = evaluateAtUvPeak(true);

        assertThat(result.acciones()).extracting(RuleAction::type)
                .doesNotContain(ActionType.ABORT_ALL)
                .contains(ActionType.MOVER_MEDIASOMBRA);
        assertThat(result.traza().reglas())
                .filteredOn(t -> t.ruleId().equals(ManualLockRule.NAME))
                .singleElement()
                .satisfies(t -> assertThat(t.estado()).isEqualTo(EstadoRegla.OMITIDA_DESHABILITADA));
    }

    @Test
    @DisplayName("con el bypass 'lock' activo la regla ya no corta y el sector se evalúa")
    void theLockBypassLetsTheSectorBeEvaluated() {
        orchestrator = orchestratorWith(simulatorOverrides(true, true));

        ResultadoEvaluacion result = evaluateAtUvPeak(true);

        assertThat(result.acciones()).extracting(RuleAction::type)
                .doesNotContain(ActionType.ABORT_ALL)
                .contains(ActionType.MOVER_MEDIASOMBRA);
    }
}
