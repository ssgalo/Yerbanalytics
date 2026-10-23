package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.supply.ApplicationPreconditions;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El plan de nutrición de punta a punta contra la base real: el {@link ZoneRuleEngine} con sus reglas, el
 * {@code NutritionPlanService}, el {@link ApplicationService} y los repositorios, sin dobles salvo la publicación MQTT.
 * Corre en una transacción que se revierte (no deja filas) sobre MZ-1, que siembra {@code data.sql}.
 *
 * <p>Las guardas horarias y de clima de las precondiciones (ventana, temperatura, lluvia, humedad, stock) se saltean con el
 * mismo bypass que usa el simulador: el test no puede depender de la hora a la que corre.
 */
@SpringBootTest
@Transactional
@DisplayName("Plan de nutrición · de punta a punta contra la base")
class NutritionPlanFlowIntegrationTest {

    private static final String ZONE_ID = "MZ-1";

    @Autowired
    private ZoneRuleEngine engine;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private ZonaRepository zones;
    @Autowired
    private SectorRepository sectorRepository;
    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private PendingApplicationRepository pending;
    @Autowired
    private RuleOverridesStore overrides;
    @Autowired
    private OperationalRuleStates states;

    @MockBean
    private ComandoActuadorPublisher publisher;

    private ZonaEntity zone;
    private List<SectorEntity> sectors;

    @BeforeEach
    void setUp() {
        zone = zones.findById(ZONE_ID).orElseThrow();
        zone.setNutritionPlanIntervalDays(null);
        zone.setLastPlanApplicationTs(null);
        zone.setNutritionPlanDueTs(null);
        zone.setNutritionBatchStartTs(null);
        zones.save(zone);
        sectors = sectorRepository.findByZonaId(ZONE_ID);
        // La orden de bomba sale bien salvo que el test diga otra cosa.
        when(publisher.publicar(anyString(), anyString(), eq("pump"), eq("ON"), anyMap()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "cmd", null));
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(Map.of("interval-fertilizer", 0.0),
                Set.of("window", "temperature", "rain", "moisture", "stock", "daily")));
    }

    @AfterEach
    void cleanOverrides() {
        overrides.clear();
        // El cache es del contexto y sobrevive al rollback: se lo deja prendido y consistente con la base.
        states.set(NutritionPlanRule.NAME, true, "test", "test");
        states.reload();
    }

    private ZoneRuleContext context(Instant now) {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), HardwareCoverage.COMPLETE));
        return new ZoneRuleContext(zone, sectors, now, hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);
    }

    private static TrazaRegla traceOf(List<TrazaRegla> trace, String ruleName) {
        return trace.stream().filter(t -> t.ruleId().equals(ruleName)).findFirst().orElseThrow();
    }

    private long planApplications() {
        return sectors.stream()
                .map(s -> applications.findFirstBySectorIdAndSupplyOrderByTsDesc(s.getId(), Supply.FERTILIZER))
                .filter(a -> a.isPresent() && NutritionPlanRule.NAME.equals(a.get().getRuleName()))
                .count();
    }

    @Test
    @DisplayName("una zona sin historial recibe la tanda: 100 aplicaciones, la tanda se cierra y el intervalo corre")
    void aNewZoneGetsTheBatch() {
        assertThat(sectors).hasSize(100);

        var trace = nutricion(engine.evaluate(context(Instant.now())));

        assertThat(planApplications()).isEqualTo(100);
        assertThat(zone.getNutritionBatchStartTs()).as("la tanda se cerró en el mismo ciclo").isNull();
        assertThat(zone.getLastPlanApplicationTs()).isNotNull();
        assertThat(trace).extracting(t -> t.ruleId())
                .containsExactly("FungalConditionRule", "OutbreakRule", "LowEcRule", "NutritionPlanRule");
        var plan = traceOf(trace, NutritionPlanRule.NAME);
        assertThat(plan.estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(plan.acciones()).extracting(a -> a.tipo().name()).contains("UPDATE_NUTRITION_PLAN", "ALERTA");
    }

    @Test
    @DisplayName("evaluar de nuevo en el mismo ciclo no duplica las aplicaciones")
    void evaluatingAgainDoesNotDuplicate() {
        engine.evaluate(context(Instant.now()));
        long first = planApplications();

        engine.evaluate(context(Instant.now()));

        assertThat(first).isEqualTo(100);
        assertThat(planApplications()).isEqualTo(100);
        assertThat(applications.findFirstByZoneIdAndSupplyOrderByTsDesc(ZONE_ID, Supply.FERTILIZER)).isPresent();
    }

    @Test
    @DisplayName("con los tanques sin registrar cada aplicación se rechaza y la tanda se cierra descartada")
    void unregisteredTanksDiscardTheBatch() {
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(Map.of("interval-fertilizer", 0.0),
                Set.of("window", "temperature", "rain", "moisture", "daily")));   // sin el bypass de stock

        engine.evaluate(context(Instant.now()));

        assertThat(planApplications()).isZero();
        assertThat(pending.countByZoneIdAndSupplyAndRuleName(ZONE_ID, Supply.FERTILIZER, NutritionPlanRule.NAME)).isZero();
        assertThat(zone.getNutritionBatchStartTs()).isNull();
        assertThat(zone.getLastPlanApplicationTs()).as("la tanda se descartó: el intervalo corre igual").isNotNull();
    }

    @Test
    @DisplayName("un sector cuya orden no sale queda pendiente: la tanda sigue abierta y no se vuelve a pedir")
    void aSectorThatCannotBeOrderedKeepsTheBatchOpen() {
        SectorEntity stuck = sectors.get(44);
        when(publisher.publicar(anyString(), eq(stuck.getId()), eq("pump"), eq("ON"), anyMap()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(false, null, "broker caído"));

        engine.evaluate(context(Instant.now()));

        assertThat(planApplications()).isEqualTo(99);
        assertThat(pending.countByZoneIdAndSupplyAndRuleName(ZONE_ID, Supply.FERTILIZER, NutritionPlanRule.NAME)).isEqualTo(1);
        assertThat(zone.getNutritionBatchStartTs()).isNotNull();
        assertThat(zone.getLastPlanApplicationTs()).as("el intervalo no corre con la tanda abierta").isNull();

        // El pulso siguiente no pide de nuevo: nadie recibe una segunda aplicación.
        engine.evaluate(context(Instant.now()));
        assertThat(planApplications()).isEqualTo(99);
        assertThat(zone.getNutritionBatchStartTs()).isNotNull();

        // Sale la ventana: el reintento aplica al que faltaba y el pulso que sigue cierra la tanda.
        when(publisher.publicar(anyString(), eq(stuck.getId()), eq("pump"), eq("ON"), anyMap()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "cmd", null));
        assertThat(applicationService.processPending()).isEqualTo(1);
        engine.evaluate(context(Instant.now()));

        assertThat(planApplications()).isEqualTo(100);
        assertThat(zone.getNutritionBatchStartTs()).isNull();
        assertThat(zone.getLastPlanApplicationTs()).isNotNull();
    }

    @Test
    @DisplayName("el intervalo propio de la zona manda: con 14 días y una aplicación de hace 10 el plan no vence")
    void theZoneIntervalRules() {
        zone.setNutritionPlanIntervalDays(14);
        zone.setLastPlanApplicationTs(Instant.now().minusSeconds(10 * 86_400L).toEpochMilli());

        engine.evaluate(context(Instant.now()));

        assertThat(planApplications()).isZero();
        assertThat(zone.getNutritionBatchStartTs()).isNull();
    }

    @Test
    @DisplayName("con el intervalo cumplido el plan vence y pide la tanda")
    void theIntervalElapses() {
        zone.setNutritionPlanIntervalDays(14);
        zone.setLastPlanApplicationTs(Instant.now().minusSeconds(15 * 86_400L).toEpochMilli());

        engine.evaluate(context(Instant.now()));

        assertThat(planApplications()).isEqualTo(100);
    }

    @Test
    @DisplayName("con la regla apagada en Motor de reglas no se pide nada")
    void disabledFromTheEngine() {
        states.set(NutritionPlanRule.NAME, false, "test", "test");
        states.reload();   // dentro de la transacción de prueba el commit no llega: se relee el cache a mano

        var trace = nutricion(engine.evaluate(context(Instant.now())));

        assertThat(planApplications()).isZero();
        assertThat(traceOf(trace, NutritionPlanRule.NAME).estado()).isEqualTo(EstadoRegla.OMITIDA_DESHABILITADA);
    }

    @Test
    @DisplayName("un sector que ya recibió fertilizante (P-D2) queda pendiente sin frenar a los demás")
    void aSectorThatAlreadyReceivedToday() {
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(Map.of(),
                Set.of("window", "temperature", "rain", "moisture", "stock")));   // sin acortar el intervalo de P-D2
        SectorEntity first = sectors.get(0);
        applicationService.request(first, Supply.FERTILIZER, "SupplyRule", false);

        engine.evaluate(context(Instant.now()));

        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(first.getId(), Supply.FERTILIZER))
                .get().extracting(a -> a.getRuleName()).as("el primer sector conserva su aplicación previa").isEqualTo("SupplyRule");
        assertThat(pending.countByZoneIdAndSupplyAndRuleName(ZONE_ID, Supply.FERTILIZER, NutritionPlanRule.NAME)).isEqualTo(1);
        assertThat(planApplications()).isEqualTo(99);
        assertThat(zone.getNutritionBatchStartTs()).isNotNull();
    }
    /** La traza de las reglas del plan y las bióticas que lo preceden (el motor de zonas también corre las de la mediasombra). */
    private static List<com.yerbanalytics.backend.engine.traza.TrazaRegla> nutricion(
            List<com.yerbanalytics.backend.engine.traza.TrazaRegla> trace) {
        return trace.stream().filter(t -> java.util.Set.of("FungalConditionRule", "OutbreakRule", "LowEcRule", "NutritionPlanRule").contains(t.ruleId())).toList();
    }
}
