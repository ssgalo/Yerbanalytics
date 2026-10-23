package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El estado del plan de nutrición vive en columnas de la zona y tiene que sobrevivir a un reinicio. Corre contra la
 * PostgreSQL de dev con rollback por test; la zona es ficticia (prefijo {@code ZT-}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Zona · columnas del plan de nutrición")
class ZonaNutritionPlanPersistenceTest {

    @Autowired
    private ZonaRepository zones;
    @Autowired
    private TestEntityManager entityManager;

    private ZonaEntity newZone(String id) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        z.setName("Zona de prueba " + id);
        z.setSub("sub");
        return z;
    }

    @Test
    @DisplayName("una zona nueva no tiene plan: sin intervalo propio, sin aplicaciones, sin tanda ni adelantamiento")
    void aNewZoneHasNoPlanState() {
        zones.saveAndFlush(newZone("ZT-5"));
        entityManager.clear();

        ZonaEntity read = zones.findById("ZT-5").orElseThrow();

        assertThat(read.getNutritionPlanIntervalDays()).isNull();
        assertThat(read.getLastPlanApplicationTs()).isNull();
        assertThat(read.getNutritionPlanDueTs()).isNull();
        assertThat(read.getNutritionBatchStartTs()).isNull();
    }

    @Test
    @DisplayName("el intervalo, la última aplicación, el adelantamiento y la tanda se guardan y se leen")
    void theStateRoundTrips() {
        ZonaEntity z = newZone("ZT-6");
        z.setNutritionPlanIntervalDays(14);
        z.setLastPlanApplicationTs(1_000L);
        z.setNutritionPlanDueTs(2_000L);
        z.setNutritionBatchStartTs(3_000L);
        zones.saveAndFlush(z);
        entityManager.clear();

        ZonaEntity read = zones.findById("ZT-6").orElseThrow();

        assertThat(read.getNutritionPlanIntervalDays()).isEqualTo(14);
        assertThat(read.getLastPlanApplicationTs()).isEqualTo(1_000L);
        assertThat(read.getNutritionPlanDueTs()).isEqualTo(2_000L);
        assertThat(read.getNutritionBatchStartTs()).isEqualTo(3_000L);
    }
}
