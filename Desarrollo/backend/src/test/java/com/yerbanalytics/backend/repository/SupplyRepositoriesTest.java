package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.model.PendingApplicationEntity;
import com.yerbanalytics.backend.model.TankEntity;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las consultas y restricciones de la aplicación de insumos contra una base real. Corre contra la PostgreSQL de dev con
 * rollback por test (no deja filas); las zonas y sectores son ficticios (prefijo {@code ZT-}) para no mezclarse con los reales.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Repositorios de aplicación de insumos")
class SupplyRepositoriesTest {

    private static final String SECTOR = "ZT-1-001";
    private static final String ZONE = "ZT-1";

    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private PendingApplicationRepository pending;
    @Autowired
    private TankRepository tanks;

    private static ApplicationEntity application(String sector, Supply supply, long ts) {
        return new ApplicationEntity(UUID.randomUUID().toString(), sector, ZONE, supply, 10, ts, "SupplyRule");
    }

    private static PendingApplicationEntity pendingApplication(String sector, Supply supply, long createdTs) {
        return new PendingApplicationEntity(UUID.randomUUID().toString(), sector, ZONE, supply, "SupplyRule", true,
                createdTs, "P-D3");
    }

    // --- application ---

    @Test
    @DisplayName("la última aplicación de un insumo en el sector es la más reciente de ese insumo")
    void lastApplicationOfASupply() {
        applications.save(application(SECTOR, Supply.FERTILIZER, 1_000));
        applications.save(application(SECTOR, Supply.FERTILIZER, 3_000));
        applications.save(application(SECTOR, Supply.FUNGICIDE, 5_000));
        applications.save(application("ZT-1-002", Supply.FERTILIZER, 9_000));
        applications.flush();

        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(SECTOR, Supply.FERTILIZER))
                .get().extracting(ApplicationEntity::getTs).isEqualTo(3_000L);
        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(SECTOR, Supply.FUNGICIDE))
                .get().extracting(ApplicationEntity::getTs).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("la última aplicación de cualquier insumo es la más reciente del sector")
    void lastApplicationOfAnySupply() {
        applications.save(application(SECTOR, Supply.FERTILIZER, 3_000));
        applications.save(application(SECTOR, Supply.FUNGICIDE, 5_000));
        applications.save(application("ZT-1-002", Supply.FERTILIZER, 9_000));
        applications.flush();

        assertThat(applications.findFirstBySectorIdOrderByTsDesc(SECTOR)).get().extracting(ApplicationEntity::getTs)
                .isEqualTo(5_000L);
        assertThat(applications.findFirstBySectorIdOrderByTsDesc("ZT-1-099")).isEmpty();
    }

    // --- application: plan de nutrición ---

    private static ApplicationEntity application(String sector, String zone, Supply supply, long ts, String rule) {
        return new ApplicationEntity(UUID.randomUUID().toString(), sector, zone, supply, 10, ts, rule);
    }

    @Test
    @DisplayName("la última fertilización de la zona es la más reciente de cualquier sector y de cualquier regla")
    void lastFertilizationOfTheZone() {
        applications.save(application("ZT-3-001", "ZT-3", Supply.FERTILIZER, 1_000, "NutritionPlanRule"));
        applications.save(application("ZT-3-002", "ZT-3", Supply.FERTILIZER, 4_000, "SupplyRule"));
        applications.save(application("ZT-3-003", "ZT-3", Supply.FUNGICIDE, 8_000, "NutritionPlanRule"));
        applications.save(application("ZT-4-001", "ZT-4", Supply.FERTILIZER, 9_000, "NutritionPlanRule"));
        applications.flush();

        assertThat(applications.findFirstByZoneIdAndSupplyOrderByTsDesc("ZT-3", Supply.FERTILIZER))
                .get().extracting(ApplicationEntity::getTs).isEqualTo(4_000L);
        assertThat(applications.findFirstByZoneIdAndSupplyOrderByTsDesc("ZT-9", Supply.FERTILIZER)).isEmpty();
    }

    @Test
    @DisplayName("la última aplicación del plan en la zona desde el inicio de la tanda ignora otras reglas y tandas anteriores")
    void lastPlanApplicationSinceTheBatchStart() {
        applications.save(application("ZT-3-001", "ZT-3", Supply.FERTILIZER, 1_000, "NutritionPlanRule"));   // tanda anterior
        applications.save(application("ZT-3-002", "ZT-3", Supply.FERTILIZER, 5_000, "NutritionPlanRule"));
        applications.save(application("ZT-3-003", "ZT-3", Supply.FERTILIZER, 6_000, "NutritionPlanRule"));
        applications.save(application("ZT-3-004", "ZT-3", Supply.FERTILIZER, 7_000, "SupplyRule"));          // correctiva
        applications.flush();

        assertThat(applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                "ZT-3", Supply.FERTILIZER, "NutritionPlanRule", 5_000))
                .get().extracting(ApplicationEntity::getTs).isEqualTo(6_000L);
        assertThat(applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                "ZT-3", Supply.FERTILIZER, "NutritionPlanRule", 6_001)).isEmpty();
    }

    // --- pending_application ---

    @Test
    @DisplayName("cuenta las pendientes de una zona, un insumo y una regla: lo que mantiene abierta la tanda del plan")
    void countsThePlanBatchPending() {
        pending.save(new PendingApplicationEntity(UUID.randomUUID().toString(), "ZT-3-001", "ZT-3", Supply.FERTILIZER,
                "NutritionPlanRule", false, 1_000L, "P-D3"));
        pending.save(new PendingApplicationEntity(UUID.randomUUID().toString(), "ZT-3-002", "ZT-3", Supply.FERTILIZER,
                "NutritionPlanRule", false, 1_000L, "P-D5"));
        pending.save(new PendingApplicationEntity(UUID.randomUUID().toString(), "ZT-3-003", "ZT-3", Supply.FERTILIZER,
                "SupplyRule", true, 1_000L, "P-D3"));
        pending.save(new PendingApplicationEntity(UUID.randomUUID().toString(), "ZT-3-001", "ZT-3", Supply.FUNGICIDE,
                "NutritionPlanRule", false, 1_000L, "P-D3"));
        pending.save(new PendingApplicationEntity(UUID.randomUUID().toString(), "ZT-4-001", "ZT-4", Supply.FERTILIZER,
                "NutritionPlanRule", false, 1_000L, "P-D3"));
        pending.flush();

        assertThat(pending.countByZoneIdAndSupplyAndRuleName("ZT-3", Supply.FERTILIZER, "NutritionPlanRule")).isEqualTo(2);
        assertThat(pending.countByZoneIdAndSupplyAndRuleName("ZT-9", Supply.FERTILIZER, "NutritionPlanRule")).isZero();
    }

    @Test
    @DisplayName("a lo sumo una pendiente por sector e insumo")
    void onePendingPerSectorAndSupply() {
        pending.saveAndFlush(pendingApplication(SECTOR, Supply.FERTILIZER, 1_000));

        assertThatThrownBy(() -> pending.saveAndFlush(pendingApplication(SECTOR, Supply.FERTILIZER, 2_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("se busca por sector e insumo, por sector, y se lista de la más vieja a la más nueva")
    void findsPending() {
        pending.save(pendingApplication(SECTOR, Supply.FUNGICIDE, 2_000));
        pending.save(pendingApplication(SECTOR, Supply.FERTILIZER, 1_000));
        pending.save(pendingApplication("ZT-1-002", Supply.FERTILIZER, 3_000));
        pending.flush();

        assertThat(pending.findBySectorIdAndSupply(SECTOR, Supply.FUNGICIDE)).get()
                .extracting(PendingApplicationEntity::getCreatedTs).isEqualTo(2_000L);
        assertThat(pending.findBySectorId(SECTOR)).hasSize(2);
        assertThat(pending.findAllByOrderByCreatedTsAsc()).extracting(PendingApplicationEntity::getCreatedTs)
                .containsSubsequence(1_000L, 2_000L, 3_000L);
        assertThat(pending.findById(pending.findBySectorIdAndSupply(SECTOR, Supply.FERTILIZER).orElseThrow().getId()))
                .get().extracting(PendingApplicationEntity::isRequiresDiagnosis).isEqualTo(true);
    }

    // --- supply_tank ---

    @Test
    @DisplayName("un solo tanque por macro-zona e insumo")
    void oneTankPerZoneAndSupply() {
        tanks.saveAndFlush(new TankEntity(null, ZONE, Supply.FERTILIZER, 50_000L, 20_000L, 1L, "ana"));

        assertThatThrownBy(() -> tanks.saveAndFlush(new TankEntity(null, ZONE, Supply.FERTILIZER, 50_000L, 1L, 2L, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("se lee el tanque con y sin bloqueo de escritura, y la lista sale por zona e insumo")
    void readsTanks() {
        tanks.save(new TankEntity(null, "ZT-2", Supply.FUNGICIDE, 40_000L, 10_000L, 1L, null));
        tanks.save(new TankEntity(null, "ZT-2", Supply.FERTILIZER, 50_000L, 20_000L, 1L, "ana"));
        tanks.flush();

        assertThat(tanks.findByZoneIdAndSupply("ZT-2", Supply.FUNGICIDE)).get()
                .extracting(TankEntity::getLevelMl).isEqualTo(10_000L);
        assertThat(tanks.lockByZoneIdAndSupply("ZT-2", Supply.FERTILIZER)).get()
                .extracting(TankEntity::getUpdatedBy).isEqualTo("ana");
        assertThat(tanks.lockByZoneIdAndSupply("ZT-9", Supply.FERTILIZER)).isEmpty();
        assertThat(tanks.findAllByOrderByZoneIdAscSupplyAsc()).filteredOn(t -> "ZT-2".equals(t.getZoneId()))
                .extracting(TankEntity::getSupply).containsExactly(Supply.FERTILIZER, Supply.FUNGICIDE);
    }
}
