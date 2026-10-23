package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.supply.ApplicationOutcome;
import com.yerbanalytics.backend.supply.ApplicationPreconditions;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.FungicideReviewService;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Las reglas bióticas de punta a punta contra la base real: el foco (F-04) recorre el {@link ZoneRuleEngine}, el
 * {@link ApplicationService} y los repositorios, y el tope de fitosanitarios (F-06) se levanta con la revisión. Sin dobles
 * salvo la publicación MQTT. Corre en una transacción que se revierte (no deja filas) sobre MZ-1, que siembra {@code data.sql}.
 *
 * <p>Las guardas horarias y de clima de las precondiciones se saltean con el mismo bypass que usa el simulador: el test no
 * puede depender de la hora a la que corre.
 */
@SpringBootTest
@Transactional
@DisplayName("Reglas bióticas · de punta a punta contra la base")
class BioticFlowIntegrationTest {

    private static final String ZONE_ID = "MZ-1";

    @Autowired
    private ZoneRuleEngine engine;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private FungicideReviewService reviews;
    @Autowired
    private ZonaRepository zones;
    @Autowired
    private SectorRepository sectorRepository;
    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private RuleOverridesStore overrides;

    @MockBean
    private ComandoActuadorPublisher publisher;

    private ZonaEntity zone;
    private List<SectorEntity> sectors;

    @BeforeEach
    void setUp() {
        zone = zones.findById(ZONE_ID).orElseThrow();
        sectors = sectorRepository.findByZonaId(ZONE_ID);
        when(publisher.publicar(anyString(), anyString(), eq("pump"), eq("ON"), anyMap()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "cmd", null));
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(true, Map.of("interval-fungicide", 0.0),
                Set.of("window", "temperature", "rain", "moisture", "stock", "daily")));
        // El plan de nutrición no es lo que se prueba acá.
        overrides.put("NutritionPlanRule", new RuleOverride(false, Map.of(), Set.of()));
        overrides.put("LowEcRule", new RuleOverride(false, Map.of(), Set.of()));
    }

    @AfterEach
    void cleanOverrides() {
        overrides.clear();
    }

    private SectorEntity sector(int n) {
        return sectors.stream().filter(s -> s.getN() == n).findFirst().orElseThrow();
    }

    private static String id(int n) {
        return String.format("%s-%03d", ZONE_ID, n);
    }

    private void diagnose(int n, String estado) {
        SectorEntity s = sector(n);
        s.setDiagnosisEstado(estado);
        s.setDiagnosisSev("Media");
        s.setDiagnosisConf(90.0);
        s.setDiagnosisTs(Instant.now().minusSeconds(3600).toEpochMilli());
        sectorRepository.save(s);
    }

    private ZoneRuleContext context() {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), HardwareCoverage.COMPLETE));
        return new ZoneRuleContext(zone, sectors, Instant.now(), hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);
    }

    private Set<String> fungicideSectors() {
        return sectors.stream()
                .filter(s -> applications.findFirstBySectorIdAndSupplyOrderByTsDesc(s.getId(), Supply.FUNGICIDE).isPresent())
                .map(SectorEntity::getId)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("tres sectores con hongo: se trata a todos sus lindantes una sola vez y el foco queda en la traza")
    void anOutbreakTreatsTheNeighborsOnce() {
        diagnose(45, "Daño fúngico");
        diagnose(46, "Daño fúngico");
        diagnose(55, "Daño fúngico");

        List<TrazaRegla> trace = engine.evaluate(context());

        // Lindantes de 45, 46 y 55 en una grilla de 10 por fila, sin repetir.
        Set<String> expected = java.util.stream.IntStream.of(34, 35, 36, 37, 44, 45, 46, 47, 54, 55, 56, 57, 64, 65, 66)
                .mapToObj(BioticFlowIntegrationTest::id).collect(Collectors.toSet());
        assertThat(fungicideSectors()).isEqualTo(expected);
        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(id(35), Supply.FUNGICIDE))
                .get().extracting(ApplicationEntity::getRuleName).isEqualTo(OutbreakRule.NAME);
        TrazaRegla outbreak = trace.stream().filter(t -> t.ruleId().equals(OutbreakRule.NAME)).findFirst().orElseThrow();
        assertThat(outbreak.acciones()).extracting(a -> a.tipo().name()).contains("ACTIVAR_BOMBA", "ALERTA");

        long applicationsAfterFirst = applications.count();
        engine.evaluate(context());
        assertThat(applications.count()).as("el foco en curso no vuelve a pedir").isEqualTo(applicationsAfterFirst);
    }

    @Test
    @DisplayName("dos sectores con hongo no son un foco: no se trata a nadie")
    void twoSectorsAreNotAnOutbreak() {
        diagnose(45, "Daño fúngico");
        diagnose(46, "Daño fúngico");

        engine.evaluate(context());

        assertThat(fungicideSectors()).isEmpty();
    }

    @Test
    @DisplayName("un sector con tres fitosanitarios en 30 días queda bloqueado hasta que alguien registre la revisión")
    void theCapBlocksUntilReviewed() {
        SectorEntity target = sector(45);
        long now = Instant.now().toEpochMilli();
        for (int days : new int[]{25, 18, 9}) {
            applications.save(new ApplicationEntity("cap-test-" + days, target.getId(), ZONE_ID, Supply.FUNGICIDE, 10,
                    now - days * 86_400_000L, "FungalDamageRule"));
        }

        ApplicationOutcome blocked = applicationService.request(target, Supply.FUNGICIDE, "FungalDamageRule", false);
        assertThat(blocked.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        assertThat(blocked.reason()).contains("F-06");

        reviews.register(target.getId(), "Revisé el sector: es otro problema", "tester");
        assertThat(sectorRepository.findById(target.getId()).orElseThrow().getFungicideReviewTs()).isNotNull();

        ApplicationOutcome afterReview = applicationService.request(target, Supply.FUNGICIDE, "FungalDamageRule", false);
        assertThat(afterReview.status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }
}
