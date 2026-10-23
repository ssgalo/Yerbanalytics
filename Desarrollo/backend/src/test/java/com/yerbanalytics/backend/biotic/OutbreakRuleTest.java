package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleOutbreakTreatment;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.supply.FungicideLog;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.biotic.BioticTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("F-04 · Foco")
class OutbreakRuleTest {

    private static final HardwareCoverage WITH_PUMP = new HardwareCoverage(true, true, true, true);

    private SectorGrid grid;
    private FungicideLog fungicides;
    private OutbreakRule rule;
    private ZonaEntity zone;

    @BeforeEach
    void setUp() {
        grid = mock(SectorGrid.class);
        when(grid.sectorsPerRow(anyInt())).thenReturn(10);
        fungicides = mock(FungicideLog.class);
        rule = new OutbreakRule(grid, fungicides);
        zone = zone("MZ-1");
    }

    /** Una zona de 100 sectores sanos con el diagnóstico dado en los números indicados. */
    private List<SectorEntity> zoneWith(String estado, long hoursAgo, int... affected) {
        Set<Integer> numbers = new java.util.HashSet<>();
        for (int n : affected) {
            numbers.add(n);
        }
        List<SectorEntity> sectors = new ArrayList<>();
        for (int n = 1; n <= 100; n++) {
            sectors.add(numbers.contains(n) ? sector(zone, n, estado, "Media", 88.0, hoursAgo) : healthy(zone, n));
        }
        return sectors;
    }

    private static void mark(List<SectorEntity> sectors, int n, String estado, long hoursAgo) {
        sectors.set(n - 1, sector(sectors.get(n - 1).getZona(), n, estado, "Media", 88.0, hoursAgo));
    }

    private ZoneRuleContext ctx(List<SectorEntity> sectors, RuleOverrides overrides) {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        return new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), false, Set.of(), overrides);
    }

    private List<RuleAction> evaluate(List<SectorEntity> sectors) {
        return rule.evaluate(ctx(sectors, RuleOverrides.NONE), new Evaluacion(rule, ReglaTestSupport.fabrica()));
    }

    private static List<String> treated(List<RuleAction> actions) {
        return actions.stream().filter(a -> a.type() == ActionType.ACTIVAR_BOMBA)
                .map(a -> ((DetalleOutbreakTreatment) a.detalle()).sectorId()).sorted().toList();
    }

    private static List<String> ids(int... numbers) {
        return java.util.Arrays.stream(numbers).mapToObj(n -> String.format("MZ-1-%03d", n)).sorted().toList();
    }

    @Test
    void tresSectoresConElMismoDiagnosticoTratanATodosSusLindantesYAvisanConUnCritical() {
        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        // Lindantes de 10 (9, 19, 20), de 11 (1, 2, 12, 21, 22) y de 21 (11, 12, 22, 31, 32), sin repetir.
        assertThat(treated(actions)).containsExactlyElementsOf(ids(1, 2, 9, 11, 12, 19, 20, 21, 22, 31, 32));
        DetalleAlerta alert = (DetalleAlerta) first(actions, ActionType.ALERTA).detalle();
        assertThat(alert.nivel()).isEqualTo(NivelAlerta.CRITICAL);
        assertThat(alert.texto()).contains("Foco de daño fúngico en MZ-1");
    }

    @Test
    void cadaOrdenEsFitosanitarioSinExigirDiagnostico() {
        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        assertThat(actions).filteredOn(a -> a.type() == ActionType.ACTIVAR_BOMBA)
                .allSatisfy(a -> assertThat(((DetalleOutbreakTreatment) a.detalle()).supply()).isEqualTo(Supply.FUNGICIDE));
    }

    @Test
    void dosSectoresNoSonFoco() {
        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void dosConHongoYUnoConPlagaNoSonFoco() {
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11);
        mark(sectors, 21, "Plaga foliar", 20);

        assertThat(evaluate(sectors)).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void elAcaroYLaPlagaFoliarSonElMismoDiagnostico() {
        List<SectorEntity> sectors = zoneWith("Plaga foliar", 20, 10, 11);
        mark(sectors, 21, "Ácaro", 20);

        List<RuleAction> actions = evaluate(sectors);

        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isTrue();
        assertThat(((DetalleAlerta) first(actions, ActionType.ALERTA).detalle()).texto()).contains("Foco de plaga foliar");
    }

    @Test
    void elDanoBioticoGenericoNoFormaFoco() {
        assertThat(has(evaluate(zoneWith("Daño biótico", 20, 10, 11, 21)), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void enElBordeDeLaZonaSoloSeTratanLosLindantesQueExisten() {
        // 1, 2 y 11 en la esquina superior izquierda: nada arriba ni a la izquierda.
        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 1, 2, 11));

        assertThat(treated(actions)).containsExactlyElementsOf(ids(1, 2, 3, 11, 12, 13, 21, 22));
    }

    @Test
    void losDiagnosticosViejosNoCuentan() {
        // El tercero llegó hace 80 h: fuera de las 72 h del foco.
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11);
        mark(sectors, 21, "Daño fúngico", 80);

        assertThat(has(evaluate(sectors), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void laVentanaDelSimuladorAmpliaLasHorasConsideradas() {
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11);
        mark(sectors, 21, "Daño fúngico", 80);

        // Cooldown `window` en minutos: 100 h.
        List<RuleAction> actions = rule.evaluate(ctx(sectors, cooldown(OutbreakRule.NAME, "window", 100 * 60)),
                new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void unDiagnosticoSinConfianzaSuficienteNoCuenta() {
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11);
        sectors.set(20, sector(zone, 21, "Daño fúngico", "Media", 40.0, 20));

        assertThat(has(evaluate(sectors), ActionType.ACTIVAR_BOMBA)).isFalse();
    }

    @Test
    void unDiagnosticoMasViejoQueLaVigenciaPeroDentroDeLaVentanaDelFocoCuenta() {
        // 60 h es más que la vigencia de P-D1 (48 h) pero menos que las 72 h del foco.
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 60, 10, 11, 21);

        assertThat(has(evaluate(sectors), ActionType.ACTIVAR_BOMBA)).isTrue();
    }

    @Test
    void unLindanteQueYaRecibioElFitosanitarioEnLaVentanaNoVuelveAPedirse() {
        when(fungicides.treatedSince(eq("MZ-1-009"), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        assertThat(treated(actions)).doesNotContain("MZ-1-009").hasSize(10);
    }

    @Test
    void unLindanteConLaAplicacionEnColaNoVuelveAPedirse() {
        when(fungicides.isPending(anyString())).thenReturn(false);
        when(fungicides.isPending("MZ-1-019")).thenReturn(true);

        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        assertThat(treated(actions)).doesNotContain("MZ-1-019").hasSize(10);
    }

    @Test
    void siTodosLosLindantesYaFueronTratadosElFocoEnCursoNoRepiteNiLaAlertaNiLasOrdenes() {
        when(fungicides.treatedSince(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        List<RuleAction> actions = evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(actions.get(0).motivo()).containsIgnoringCase("foco").contains("ya recibieron");
    }

    @Test
    void unLindanteSinBombaNoSePideYElFocoIgualSeAvisa() {
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11, 21);
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        hardware.put("MZ-1-009", new HardwareCoverage(true, false, true, true));
        ZoneRuleContext ctx = new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);

        List<RuleAction> actions = rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(treated(actions)).doesNotContain("MZ-1-009").hasSize(10);
        assertThat(has(actions, ActionType.ALERTA)).isTrue();
    }

    @Test
    void conLaZonaBloqueadaElFocoSeAvisaPeroNoSePideNada() {
        List<SectorEntity> sectors = zoneWith("Daño fúngico", 20, 10, 11, 21);
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        ZoneRuleContext ctx = new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), true, Set.of(), RuleOverrides.NONE);

        List<RuleAction> actions = rule.evaluate(ctx, new Evaluacion(rule, ReglaTestSupport.fabrica()));

        assertThat(has(actions, ActionType.ACTIVAR_BOMBA)).isFalse();
        assertThat(has(actions, ActionType.ALERTA)).isTrue();
    }

    @Test
    void unaZonaSanaNoHaceNada() {
        List<RuleAction> actions = evaluate(zoneWith("Sano", 20));

        assertThat(actions).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void laVentanaSeCuentaDesdeAhora() {
        // Contra el reloj de la regla: un tratamiento de hace 72 h no cuenta como «en la ventana».
        evaluate(zoneWith("Daño fúngico", 20, 10, 11, 21));

        org.mockito.Mockito.verify(fungicides, org.mockito.Mockito.atLeastOnce())
                .treatedSince(anyString(), eq(NOW.minus(Duration.ofHours(72))));
    }

    @Test
    void esUnaReglaDeMacroZonaDeLaRamaDeInsumosConCooldownDeVentana() {
        assertThat(rule.branch()).isEqualTo(RuleBranch.INSUMO);
        assertThat(rule.cooldownKeys()).containsExactly("window");
        assertThat(rule.name()).isEqualTo("OutbreakRule");
        assertThat(rule.label()).contains("F-04");
        assertThat(rule.parametros()).extracting(p -> p.clave()).containsExactlyInAnyOrder(
                "biotico.sectores-foco", "biotico.horas-foco", "diagnostico.confianza-minima");
    }
}
