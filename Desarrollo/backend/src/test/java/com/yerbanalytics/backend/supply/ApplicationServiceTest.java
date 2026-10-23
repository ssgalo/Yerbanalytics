package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.PendingApplicationEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApplicationService · aplicar un insumo")
class ApplicationServiceTest {

    private static final String SECTOR_ID = "MZ-1-045";

    /** Reloj que se puede mover: el reintento de las diferidas depende de la hora. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZonaHorariaVivero.ZONA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static Instant at(String localTime) {
        return Instant.parse("2026-10-22T" + localTime + ":00-03:00");
    }

    private MutableClock clock;
    private final List<ApplicationEntity> savedApplications = new ArrayList<>();
    private final Map<String, PendingApplicationEntity> pendingRows = new HashMap<>();
    private ApplicationRepository applications;
    private PendingApplicationRepository pending;
    private DiagnosticoRepository diagnoses;
    private com.yerbanalytics.backend.repository.SectorRepository sectors;
    private HistorialService history;
    private HistorialRepository historyRepository;
    private TankService tanks;
    private ComandoActuadorPublisher publisher;
    private WeatherService weather;
    private RuleOverridesStore overrides;
    private SectorEntity sector;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(at("08:00"));

        applications = mock(ApplicationRepository.class);
        when(applications.save(any())).thenAnswer(i -> {
            ApplicationEntity a = i.getArgument(0);
            savedApplications.add(a);
            return a;
        });
        when(applications.findFirstBySectorIdAndSupplyOrderByTsDesc(any(), any())).thenAnswer(i ->
                savedApplications.stream().filter(a -> a.getSupply() == i.getArgument(1))
                        .max(Comparator.comparingLong(ApplicationEntity::getTs)));
        when(applications.findFirstBySectorIdOrderByTsDesc(any())).thenAnswer(i ->
                savedApplications.stream().max(Comparator.comparingLong(ApplicationEntity::getTs)));
        when(applications.countBySectorIdAndSupplyAndTsGreaterThanEqual(any(), any(), anyLong())).thenAnswer(i ->
                savedApplications.stream().filter(a -> a.getSupply() == i.getArgument(1)
                        && a.getTs() >= (long) i.getArgument(2)).count());

        pending = mock(PendingApplicationRepository.class);
        when(pending.save(any())).thenAnswer(i -> {
            PendingApplicationEntity p = i.getArgument(0);
            pendingRows.put(p.getId(), p);
            return p;
        });
        org.mockito.Mockito.doAnswer(i -> pendingRows.remove(((PendingApplicationEntity) i.getArgument(0)).getId()))
                .when(pending).delete(any(PendingApplicationEntity.class));
        when(pending.findById(any())).thenAnswer(i -> Optional.ofNullable(pendingRows.get((String) i.getArgument(0))));
        when(pending.findBySectorIdAndSupply(any(), any())).thenAnswer(i -> pendingRows.values().stream()
                .filter(p -> p.getSectorId().equals(i.getArgument(0)) && p.getSupply() == i.getArgument(1)).findFirst());
        when(pending.findAllByOrderByCreatedTsAsc()).thenAnswer(i -> pendingRows.values().stream()
                .sorted(Comparator.comparingLong(PendingApplicationEntity::getCreatedTs)).toList());

        diagnoses = mock(DiagnosticoRepository.class);
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(any())).thenReturn(Optional.empty());

        ZonaEntity zone = new ZonaEntity();
        zone.setId("MZ-1");
        zone.setName("Macro-zona 1");
        zone.setTempRaw(22.0);
        zone.setHumSusRaw(55.0);
        zone.setHumSusTs(at("07:30").toEpochMilli());
        sector = new SectorEntity();
        sector.setId(SECTOR_ID);
        sector.setZona(zone);
        sector.setActuadorPump("En espera");

        sectors = mock(com.yerbanalytics.backend.repository.SectorRepository.class);
        when(sectors.findById(SECTOR_ID)).thenReturn(Optional.of(sector));

        history = mock(HistorialService.class);
        historyRepository = mock(HistorialRepository.class);
        when(historyRepository.findFirstBySectorIdAndTipoOrderByTsDesc(any(), any())).thenReturn(Optional.empty());

        DespachoRiego irrigation = mock(DespachoRiego.class);
        weather = mock(WeatherService.class);
        tanks = mock(TankService.class);
        when(tanks.check(any(), any(), anyInt())).thenReturn(TankService.Stock.OK);

        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c-1", null));

        overrides = new RuleOverridesStore();
        CatalogoParametrosService catalog = mock(CatalogoParametrosService.class);
        when(catalog.vigentes()).thenReturn(new CatalogoParametros(List.of()).fabricas());

        FungicideCap cap = new FungicideCap(new FungicideLog(applications, pending), history, clock);
        service = new ApplicationService(applications, pending, diagnoses, sectors, historyRepository, history,
                irrigation, weather, new ApplicationPreconditions(), tanks, cap, publisher, overrides, catalog, clock,
                new NoopTransactions());
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private ApplicationOutcome requestFertilizer() {
        return service.request(sector, Supply.FERTILIZER, "SupplyRule", false);
    }

    private static DiagnosticoEntity diagnosis(double conf, long hoursAgo) {
        DiagnosticoEntity d = new DiagnosticoEntity();
        d.setEstado("Clorosis");
        d.setConf(conf);
        d.setCreadoEn(at("08:00").minusSeconds(hoursAgo * 3600).toEpochMilli());
        return d;
    }

    // --- Aplicación normal ---

    @Test
    void normalApplicationPublishesTheDoseInMillilitersAndRecordsIt() {
        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
        // El backend manda mililitros; la duración la calcula y la corta el nodo (no hay durationSec).
        verify(publisher).publicar("MZ-1", SECTOR_ID, "pump", "ON", Map.of("ml", 10));
        assertThat(savedApplications).singleElement().satisfies(a -> {
            assertThat(a.getSectorId()).isEqualTo(SECTOR_ID);
            assertThat(a.getSupply()).isEqualTo(Supply.FERTILIZER);
            assertThat(a.getMl()).isEqualTo(10);
            assertThat(a.getTs()).isEqualTo(at("08:00").toEpochMilli());
            assertThat(a.getRuleName()).isEqualTo("SupplyRule");
        });
        verify(history).registrarInsumo(sector, 10, "Fertilizante", "SupplyRule");
        verify(tanks).consume("MZ-1", Supply.FERTILIZER, 10);
        assertThat(sector.getActuadorPump()).isEqualTo("Dosificando");
        assertThat(pendingRows).isEmpty();
    }

    @Test
    void theDoseComesFromTheCatalogPerSupply() {
        service.request(sector, Supply.FUNGICIDE, "Rule", false);

        verify(publisher).publicar("MZ-1", SECTOR_ID, "pump", "ON", Map.of("ml", 10));
        verify(tanks).consume("MZ-1", Supply.FUNGICIDE, 10);
    }

    @Test
    void aFungicideMarksTheSectorReentryForTwentyFourHours() {
        service.request(sector, Supply.FUNGICIDE, "Rule", false);

        assertThat(sector.getReentryUntil()).isEqualTo(at("08:00").plus(Duration.ofHours(24)).toEpochMilli());
    }

    @Test
    void aFertilizerDoesNotRestrictReentry() {
        requestFertilizer();

        assertThat(sector.getReentryUntil()).isNull();
    }

    // --- Diferidas ---

    @Test
    void outsideTheWindowTheApplicationIsDeferredAndKeptPending() {
        clock.set(at("12:00"));

        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        assertThat(outcome.reason()).contains("P-D3");
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        assertThat(pendingRows.values()).singleElement().satisfies(p -> {
            assertThat(p.getSupply()).isEqualTo(Supply.FERTILIZER);
            assertThat(p.getRuleName()).isEqualTo("SupplyRule");
            assertThat(p.getLastReason()).contains("P-D3");
        });
        verify(history).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), eq("SupplyRule"), any());
    }

    @Test
    void theSamePendingIsNotDuplicatedNorRecordedTwiceWhileTheReasonDoesNotChange() {
        clock.set(at("12:00"));

        requestFertilizer();
        requestFertilizer();

        assertThat(pendingRows).hasSize(1);
        verify(history, times(1)).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), any(), any());
    }

    @Test
    void aDeferredApplicationRunsWhenTheNextWindowOpens() {
        clock.set(at("12:00"));
        requestFertilizer();

        clock.set(at("16:59"));
        assertThat(service.processPending()).isZero();
        assertThat(pendingRows).hasSize(1);

        clock.set(at("17:00"));
        assertThat(service.processPending()).isEqualTo(1);

        verify(publisher).publicar("MZ-1", SECTOR_ID, "pump", "ON", Map.of("ml", 10));
        assertThat(pendingRows).isEmpty();
        assertThat(savedApplications).hasSize(1);
    }

    @Test
    void aPendingApplicationCancelledByItsRuleIsNotRun() {
        clock.set(at("12:00"));
        requestFertilizer();

        service.cancelPending(SECTOR_ID, Supply.FERTILIZER);
        clock.set(at("17:00"));

        assertThat(service.processPending()).isZero();
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void aPendingApplicationExpiresAfterTheConfiguredDays() {
        clock.set(at("12:00"));
        requestFertilizer();

        clock.set(at("17:00").plus(Duration.ofDays(7)));
        assertThat(service.processPending()).isZero();

        assertThat(pendingRows).isEmpty();
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        verify(history, times(2)).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), any(), any());
    }

    // --- P-D1 ---

    @Test
    void anOldDiagnosisRejectsTheApplicationAndDoesNotQueueIt() {
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(90, 60)));

        ApplicationOutcome outcome = service.request(sector, Supply.FERTILIZER, "SupplyRule", true);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        assertThat(outcome.reason()).contains("P-D1").contains("60 h");
        assertThat(pendingRows).isEmpty();
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void aFreshConfidentDiagnosisLetsTheApplicationThrough() {
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(90, 2)));

        assertThat(service.request(sector, Supply.FERTILIZER, "SupplyRule", true).status())
                .isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void anApplicationNotTriggeredByADiagnosisDoesNotLookAtOne() {
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(10, 500)));

        assertThat(service.request(sector, Supply.FERTILIZER, "NutritionPlan", false).status())
                .isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void aPendingApplicationRevalidatesTheDiagnosisWhenItRetries() {
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(90, 40)));
        clock.set(at("12:00"));
        assertThat(service.request(sector, Supply.FERTILIZER, "SupplyRule", true).status())
                .isEqualTo(ApplicationOutcome.Status.DEFERRED);

        // Al abrir la ventana de la tarde el diagnóstico ya pasó de las 48 h.
        clock.set(at("17:00"));
        assertThat(service.processPending()).isZero();

        assertThat(pendingRows).isEmpty();
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
    }

    // --- P-D2 ---

    @Test
    void fungicideAndFertilizerTheSameDayApplyTheFungicideFirst() {
        // Los dos quedaron pendientes fuera de la ventana.
        clock.set(at("12:00"));
        service.request(sector, Supply.FERTILIZER, "N-01", false);
        service.request(sector, Supply.FUNGICIDE, "F-F1", false);
        assertThat(pendingRows).hasSize(2);

        clock.set(at("17:00"));
        assertThat(service.processPending()).isEqualTo(1);

        assertThat(savedApplications).singleElement().satisfies(a -> assertThat(a.getSupply()).isEqualTo(Supply.FUNGICIDE));
        // El fertilizante sigue pendiente: queda para el día siguiente.
        assertThat(pendingRows.values()).singleElement()
                .satisfies(p -> assertThat(p.getSupply()).isEqualTo(Supply.FERTILIZER));
    }

    @Test
    void theFertilizerOfTheFollowingDayIsAppliedAfterTheFungicide() {
        clock.set(at("12:00"));
        service.request(sector, Supply.FERTILIZER, "N-01", false);
        service.request(sector, Supply.FUNGICIDE, "F-F1", false);
        clock.set(at("17:00"));
        service.processPending();
        sector.setActuadorPump("En espera");   // el ack de la bomba cerró la dosis

        clock.set(at("08:00").plus(Duration.ofDays(1)));
        assertThat(service.processPending()).isEqualTo(1);

        assertThat(savedApplications).extracting(ApplicationEntity::getSupply)
                .containsExactly(Supply.FUNGICIDE, Supply.FERTILIZER);
    }

    @Test
    void aFertilizerRequestedWhileAnEligibleFungicideWaitsGoesAfterIt() {
        clock.set(at("12:00"));
        service.request(sector, Supply.FUNGICIDE, "F-F1", false);

        clock.set(at("08:00").plus(Duration.ofDays(1)));
        ApplicationOutcome outcome = service.request(sector, Supply.FERTILIZER, "N-01", false);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        assertThat(outcome.reason()).contains("fitosanitario");
    }

    @Test
    void aFungicideWaitingOnItsOwnIntervalDoesNotHoldBackTheFertilizer() {
        // Se aplicó un fitosanitario hace 2 días: el próximo no sale hasta dentro de 5, pero el fertilizante sí.
        savedApplications.add(new ApplicationEntity("a-1", SECTOR_ID, "MZ-1", Supply.FUNGICIDE, 10,
                at("08:00").minus(Duration.ofDays(2)).toEpochMilli(), "F-F1"));
        PendingApplicationEntity waiting = new PendingApplicationEntity("p-1", SECTOR_ID, "MZ-1", Supply.FUNGICIDE,
                "F-F1", false, at("08:00").minus(Duration.ofHours(5)).toEpochMilli(), "P-D2");
        pendingRows.put(waiting.getId(), waiting);

        assertThat(service.request(sector, Supply.FERTILIZER, "N-01", false).status())
                .isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void theSecondApplicationOfTheDayIsDeferredToTomorrow() {
        requestFertilizer();
        sector.setActuadorPump("En espera");

        clock.advance(Duration.ofHours(1));   // 09:00, dentro de la ventana
        ApplicationOutcome outcome = service.request(sector, Supply.FUNGICIDE, "F-F1", false);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        assertThat(outcome.reason()).contains("hoy");
    }

    @Test
    void aSectorThatIsAlreadyDosingIsNotOrderedAgain() {
        sector.setActuadorPump("Dosificando");

        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
    }

    // --- Stock ---

    @Test
    void anEmptyTankRejectsTheApplicationAndRaisesTheAlert() {
        when(tanks.check("MZ-1", Supply.FUNGICIDE, 10)).thenReturn(TankService.Stock.INSUFFICIENT);

        ApplicationOutcome outcome = service.request(sector, Supply.FUNGICIDE, "F-F1", false);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        verify(tanks).reportShortage("MZ-1", Supply.FUNGICIDE, TankService.Stock.INSUFFICIENT);
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        verify(tanks, never()).consume(any(), any(), anyInt());
    }

    @Test
    void anUnregisteredTankIsNotAFullTank() {
        when(tanks.check("MZ-1", Supply.FERTILIZER, 10)).thenReturn(TankService.Stock.UNREGISTERED);

        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        assertThat(outcome.reason()).contains("no está registrado");
    }

    @Test
    void theStockBypassSkipsTheTankCheck() {
        when(tanks.check(any(), any(), anyInt())).thenReturn(TankService.Stock.UNREGISTERED);
        overrides.put(ApplicationPreconditions.NAME,
                new RuleOverride(true, Map.of(), java.util.Set.of(ApplicationPreconditions.BYPASS_STOCK)));

        assertThat(requestFertilizer().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    // --- Publicación ---

    @Test
    void ifTheOrderCannotBePublishedNothingIsRecordedAndItStaysPending() {
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(false, "c-2", "broker caído"));

        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        assertThat(savedApplications).isEmpty();
        verify(tanks, never()).consume(any(), any(), anyInt());
        assertThat(pendingRows).hasSize(1);
        assertThat(sector.getActuadorPump()).isEqualTo("En espera");
    }

    // --- Control desde el simulador ---

    @Test
    void turnedOffNoApplicationRunsAndItLeavesARecord() {
        overrides.put(ApplicationPreconditions.NAME, new RuleOverride(false, Map.of(), java.util.Set.of()));

        ApplicationOutcome outcome = requestFertilizer();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        verify(history).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), eq("SupplyRule"), any());
    }

    @Test
    void theWindowBypassLetsAnApplicationRunAtNoon() {
        clock.set(at("12:00"));
        overrides.put(ApplicationPreconditions.NAME,
                new RuleOverride(true, Map.of(), java.util.Set.of(ApplicationPreconditions.BYPASS_WINDOW)));

        assertThat(requestFertilizer().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    // --- F-06: tope de fitosanitarios ---

    /** Un fitosanitario ya aplicado al sector {@code daysAgo} días antes de las 08:00. */
    private void fungicideAppliedDaysAgo(int daysAgo) {
        savedApplications.add(new ApplicationEntity("a-" + daysAgo, SECTOR_ID, "MZ-1", Supply.FUNGICIDE, 10,
                at("08:00").minus(Duration.ofDays(daysAgo)).toEpochMilli(), "F-F1"));
    }

    private void threeFungicidesInTheWindow() {
        fungicideAppliedDaysAgo(25);
        fungicideAppliedDaysAgo(18);
        fungicideAppliedDaysAgo(9);
    }

    private ApplicationOutcome requestFungicide() {
        return service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", false);
    }

    @Test
    void theFourthFungicideInThirtyDaysIsRejectedWithTheCapAsTheReason() {
        threeFungicidesInTheWindow();

        ApplicationOutcome outcome = requestFungicide();

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        assertThat(outcome.reason()).contains("F-06").contains("tope");
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        assertThat(pendingRows).isEmpty();
    }

    @Test
    void theCapRaisesAWarningForTheMacroZone() {
        threeFungicidesInTheWindow();

        requestFungicide();

        org.mockito.ArgumentCaptor<DetalleAlerta> alert = org.mockito.ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq("FungicideCap"), alert.capture(), anyLong());
        assertThat(alert.getValue().nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(alert.getValue().texto()).contains(SECTOR_ID).contains("revisión");
    }

    @Test
    void theCapWarningIsNotRepeatedWhileTheBlockStays() {
        threeFungicidesInTheWindow();

        requestFungicide();
        requestFungicide();

        verify(history, times(1)).registrarAlerta(any(), any(), eq("FungicideCap"), any(), anyLong());
    }

    @Test
    void fungicidesOlderThanTheWindowDoNotCount() {
        fungicideAppliedDaysAgo(45);
        fungicideAppliedDaysAgo(40);
        fungicideAppliedDaysAgo(35);

        assertThat(requestFungicide().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void aRegisteredReviewLiftsTheBlock() {
        threeFungicidesInTheWindow();
        sector.setFungicideReviewTs(at("08:00").minus(Duration.ofDays(1)).toEpochMilli());

        // El último se aplicó hace 9 días: el intervalo de 7 ya pasó y la revisión dejó el conteo en cero.
        assertThat(requestFungicide().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void afterTheReviewOnlyTheNewApplicationsCount() {
        threeFungicidesInTheWindow();
        sector.setFungicideReviewTs(at("08:00").minus(Duration.ofDays(8)).toEpochMilli());

        assertThat(requestFungicide().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void theCapOnlyLimitsFungicidesNotFertilizers() {
        threeFungicidesInTheWindow();

        assertThat(requestFertilizer().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    @Test
    void theCapBypassLetsTheFourthFungicideThrough() {
        threeFungicidesInTheWindow();
        overrides.put(ApplicationPreconditions.NAME,
                new RuleOverride(true, Map.of(), java.util.Set.of(ApplicationPreconditions.BYPASS_CAP)));

        assertThat(requestFungicide().status()).isEqualTo(ApplicationOutcome.Status.APPLIED);
    }

    // --- Un diagnóstico, una aplicación (D-4) ---

    private static final long DIAGNOSIS_TS = at("06:00").toEpochMilli();

    private void sectorWithDiagnosis(long ts) {
        sector.setDiagnosisTs(ts);
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(90, 2)));
    }

    @Test
    void theDiagnosisIsUsedWhenTheApplicationRuns() {
        sectorWithDiagnosis(DIAGNOSIS_TS);

        service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(DIAGNOSIS_TS);
    }

    @Test
    void aDeferredApplicationDoesNotUseTheDiagnosisYet() {
        sectorWithDiagnosis(DIAGNOSIS_TS);
        clock.set(at("12:00"));

        ApplicationOutcome outcome = service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.DEFERRED);
        assertThat(sector.getDiagnosisUsadoTs()).isNull();
        assertThat(pendingRows.values()).singleElement()
                .satisfies(p -> assertThat(p.getDiagnosisTs()).isEqualTo(DIAGNOSIS_TS));
    }

    @Test
    void theDiagnosisIsUsedWhenTheDeferredApplicationFinallyRuns() {
        sectorWithDiagnosis(DIAGNOSIS_TS);
        clock.set(at("12:00"));
        service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        clock.set(at("17:00"));
        assertThat(service.processPending()).isEqualTo(1);

        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(DIAGNOSIS_TS);
    }

    @Test
    void aNewerDiagnosisThatArrivedWhileWaitingStaysNew() {
        sectorWithDiagnosis(DIAGNOSIS_TS);
        clock.set(at("12:00"));
        service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        long newer = at("14:00").toEpochMilli();
        sector.setDiagnosisTs(newer);
        clock.set(at("17:00"));
        service.processPending();

        // Se consumió el que originó el pedido; el más nuevo todavía no lo vio ninguna regla.
        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(DIAGNOSIS_TS).isLessThan(sector.getDiagnosisTs());
    }

    @Test
    void aRejectedApplicationAlsoUsesTheDiagnosisBecauseItIsNeverRetried() {
        when(diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(SECTOR_ID)).thenReturn(Optional.of(diagnosis(10, 2)));
        sector.setDiagnosisTs(DIAGNOSIS_TS);

        ApplicationOutcome outcome = service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        assertThat(outcome.status()).isEqualTo(ApplicationOutcome.Status.REJECTED);
        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(DIAGNOSIS_TS);
    }

    @Test
    void anApplicationNotDrivenByTheDiagnosisNeverTouchesIt() {
        sector.setDiagnosisTs(DIAGNOSIS_TS);

        requestFertilizer();

        assertThat(sector.getDiagnosisUsadoTs()).isNull();
    }

    @Test
    void usingADiagnosisNeverGoesBackwards() {
        sectorWithDiagnosis(DIAGNOSIS_TS);
        sector.setDiagnosisUsadoTs(at("07:00").toEpochMilli());

        service.request(sector, Supply.FUNGICIDE, "FungalDamageRule", true);

        assertThat(sector.getDiagnosisUsadoTs()).isEqualTo(at("07:00").toEpochMilli());
    }
}
