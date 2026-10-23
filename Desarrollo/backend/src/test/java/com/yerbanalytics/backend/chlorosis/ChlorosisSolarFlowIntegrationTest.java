package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.ResultadoEvaluacion;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingOrigin;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import com.yerbanalytics.backend.supply.ApplicationPreconditions;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Las reglas de clorosis y estrés solar de punta a punta contra la base real: el {@link RuleOrchestrator} con la cadena
 * completa, el {@link ActionExecutor}, el {@code ApplicationService}, la cola de riego y el historial de lecturas, sin dobles
 * salvo la publicación MQTT. Corre en una transacción que se revierte (no deja filas) sobre un sector de MZ-1, que siembra
 * {@code data.sql}.
 *
 * <p>Las guardas horarias y de clima de las precondiciones de aplicación se saltean con el mismo bypass que usa el simulador:
 * el test no puede depender de la hora a la que corre. La hora de la evaluación es el mediodía, dentro de la ventana de riego.
 */
@SpringBootTest
@Transactional
@DisplayName("Clorosis y estrés solar · de punta a punta contra la base")
class ChlorosisSolarFlowIntegrationTest {

    private static final String ZONE_ID = "MZ-1";

    @Autowired
    private RuleOrchestrator orchestrator;
    @Autowired
    private ActionExecutor executor;
    @Autowired
    private ColaRiego queue;
    @Autowired
    private ZonaRepository zones;
    @Autowired
    private SectorRepository sectorRepository;
    @Autowired
    private ZoneReadingRepository readings;
    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private DiagnosticoRepository diagnoses;
    @Autowired
    private RuleOverridesStore overrides;

    @MockBean
    private ComandoActuadorPublisher publisher;

    private ZonaEntity zone;
    private SectorEntity sector;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = java.time.ZonedDateTime.of(java.time.LocalDate.now(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA),
                java.time.LocalTime.NOON, com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA).toInstant();
        zone = zones.findById(ZONE_ID).orElseThrow();
        // La lectura de la zona es de ahora: un sensor viejo corta el riego (StaleSensorRule) y no es lo que se prueba acá.
        zone.setLastReadingTime(now.toEpochMilli());
        zone.setHumSusTs(now.toEpochMilli());
        zones.save(zone);
        sector = sectorRepository.findByZonaId(ZONE_ID).get(0);
        when(publisher.publicar(anyString(), anyString(), eq("pump"), eq("ON"), anyMap()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "cmd", null));
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(Map.of("interval-fertilizer", 0.0),
                Set.of("window", "temperature", "rain", "moisture", "stock", "daily")));
        queue.limpiar();
    }

    @AfterEach
    void cleanUp() {
        overrides.clear();
        queue.limpiar();
    }

    private void diagnose(String estado, String severity) {
        sector.setDiagnosisEstado(estado);
        sector.setDiagnosisSev(severity);
        sector.setDiagnosisConf(90.0);
        sector.setDiagnosisTs(now.minusSeconds(3600).toEpochMilli());
        sector.setDiagnosisUsadoTs(null);
        sectorRepository.save(sector);
        // El ApplicationService vuelve a verificar la vigencia (P-D1) sobre el último diagnóstico registrado del sector.
        DiagnosticoEntity registered = new DiagnosticoEntity();
        registered.setId("flow-test-" + estado);
        registered.setSectorId(sector.getId());
        registered.setZonaId(ZONE_ID);
        registered.setCapturaId("flow-test-capture");
        registered.setEstado(estado);
        registered.setConf(90.0);
        registered.setSev(severity);
        registered.setCreadoEn(sector.getDiagnosisTs());
        diagnoses.save(registered);
    }

    private void reading(double hoursAgo, Double humSus, Double uv, Double temp) {
        ZoneReadingEntity r = new ZoneReadingEntity();
        r.setZoneId(ZONE_ID);
        long ts = now.minusSeconds(Math.round(hoursAgo * 3600)).toEpochMilli();
        r.setTs(ts);
        r.setReceivedTs(ts);
        r.setOrigin(ReadingOrigin.PULSE);
        r.setHumSus(humSus);
        r.setUv(uv);
        r.setTemp(temp);
        readings.save(r);
    }

    private ResultadoEvaluacion evaluate(Double humSus, Double ce, Double ph) {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("humSus", "Humedad de sustrato", "%", humSus, "", "ok", "#000", null));
        metrics.add(new Metric("ce", "CE", "dS/m", ce, "", "ok", "#000", null));
        metrics.add(new Metric("phSuelo", "pH", "", ph, "", "ok", "#000", null));
        RuleContext ctx = new RuleContext(sector, zone, List.of(), metrics,
                com.yerbanalytics.backend.engine.RuleContextTestFactory.defaultConfig(), "ok", now, null, false,
                ContextoRiego.vacio(), RuleOverrides.NONE, Set.of(), HardwareCoverage.COMPLETE, HardwareFaults.NONE);
        ResultadoEvaluacion result = orchestrator.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
        executor.execute(result.acciones(), ctx, OrigenEvaluacion.TELEMETRIA);
        if (result.usoDiagnostico()) {
            sector.setDiagnosisUsadoTs(sector.getDiagnosisTs());
        }
        return result;
    }

    private static TrazaRegla rule(ResultadoEvaluacion result, String name) {
        return result.traza().reglas().stream().filter(r -> r.ruleId().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("clorosis nutricional: se pide fertilizante, queda aplicado y el diagnóstico se da por usado")
    void nutritionalChlorosisFertilizes() {
        diagnose("Clorosis", "Media");
        reading(10, 60.0, 50.0, 22.0);

        ResultadoEvaluacion result = evaluate(60.0, 0.8, 5.5);

        assertThat(rule(result, "ChlorosisNonNutritionalRule").estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(rule(result, "ChlorosisNutritionalRule").acciones()).extracting(a -> a.tipo())
                .contains(ActionType.ACTIVAR_BOMBA, ActionType.ALERTA);
        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(sector.getId(), Supply.FERTILIZER))
                .get().extracting(a -> a.getRuleName()).isEqualTo("ChlorosisNutritionalRule");
        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(sector.getDiagnosisTs());
    }

    @Test
    @DisplayName("clorosis con pH 6,8: F-C1 corta la rama, F-C2 no se alcanza y no se aplica nada")
    void highPhBlocksTheFertilizer() {
        diagnose("Clorosis", "Media");
        reading(10, 60.0, 50.0, 22.0);

        ResultadoEvaluacion result = evaluate(60.0, 0.8, 6.8);

        assertThat(rule(result, "ChlorosisNonNutritionalRule").acciones()).extracting(a -> a.tipo())
                .contains(ActionType.ABORT_INSUMO, ActionType.ALERTA);
        assertThat(rule(result, "ChlorosisNutritionalRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(sector.getId(), Supply.FERTILIZER)).isEmpty();
        assertThat(sector.getDiagnosisUsadoTs()).as("F-C1 se evaluó: el diagnóstico quedó usado").isEqualTo(sector.getDiagnosisTs());
    }

    @Test
    @DisplayName("asfixia radicular: el promedio de 48 h del historial de lecturas descarta el fertilizante")
    void waterloggedSubstrateBlocksTheFertilizer() {
        diagnose("Clorosis", "Media");
        for (int h = 2; h <= 46; h += 4) {
            reading(h, 86.0, 50.0, 22.0);
        }

        ResultadoEvaluacion result = evaluate(84.0, 0.8, 5.5);

        assertThat(rule(result, "ChlorosisNonNutritionalRule").acciones()).extracting(a -> a.tipo()).contains(ActionType.ABORT_INSUMO);
        assertThat(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(sector.getId(), Supply.FERTILIZER)).isEmpty();
    }

    @Test
    @DisplayName("estrés solar con luz de 88 % ayer al mediodía y sustrato en 50 %: queda en cola un riego de reposición")
    void solarStressWithHighLightQueuesAReplenishmentIrrigation() {
        diagnose("Estrés solar", "Alta");
        reading(24, 55.0, 88.0, 29.0);
        reading(2, 52.0, 45.0, 28.0);

        ResultadoEvaluacion result = evaluate(50.0, 0.8, 5.5);

        assertThat(rule(result, "SolarStressRule").acciones()).extracting(a -> a.tipo())
                .contains(ActionType.ACTIVAR_VALVULA, ActionType.ALERTA);
        SolicitudRiego request = queue.solicitudDe(ZONE_ID, sector.getId());
        assertThat(request).isNotNull();
        assertThat(request.regla()).isEqualTo("SolarStressRule");
        assertThat(request.esDeficitCritico()).isFalse();
        assertThat(rule(result, "SolarStressUnexplainedRule").acciones()).extracting(a -> a.tipo())
                .as("hubo radiación alta: F-S3 no avisa").doesNotContain(ActionType.ALERTA);
    }

    @Test
    @DisplayName("estrés solar sin radiación (luz 55 %, 27 °C): no se riega y queda el aviso de F-S3")
    void solarStressWithoutRadiationOnlyWarns() {
        diagnose("Estrés solar", "Media");
        reading(24, 55.0, 55.0, 27.0);
        reading(2, 52.0, 40.0, 25.0);

        ResultadoEvaluacion result = evaluate(50.0, 0.8, 5.5);

        assertThat(rule(result, "SolarStressRule").acciones()).extracting(a -> a.tipo()).containsExactly(ActionType.NOOP_INFO);
        assertThat(rule(result, "SolarStressUnexplainedRule").acciones()).extracting(a -> a.tipo()).contains(ActionType.ALERTA);
        assertThat(queue.solicitudDe(ZONE_ID, sector.getId())).isNull();
    }
}
