package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.effectiveness.EffectivenessBlockService;
import com.yerbanalytics.backend.effectiveness.EffectivenessService;
import com.yerbanalytics.backend.effectiveness.FollowUp;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DeclaredParameters;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.parametros.ParametrosInsumo;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.model.PendingApplicationEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.service.SupplyDoseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El único camino para aplicar un insumo (reglas_v2 §8.1–8.3). Una regla DECIDE que a un sector le corresponde
 * fertilizante o fitosanitario y se lo pide a {@link #request}; este servicio EJECUTA: verifica las precondiciones
 * ({@link ApplicationPreconditions}), el stock del tanque, publica la orden de bomba con los mililitros (el nodo
 * los convierte a tiempo con su calibración y corta solo), registra la aplicación, marca el reingreso tras un
 * fitosanitario y descuenta el tanque. Mismo reparto que el riego: las reglas deciden, el despacho abre.
 *
 * <p>Lo que no es el momento no se pierde: queda <b>pendiente</b> y {@link #processPending} lo reintenta en cada
 * ventana apta, hasta que la regla de origen lo cancele ({@link #cancelPending}), deje de ser válido (P-D1) o venza
 * ({@code insumo.pendiente-vence-dias}). Todo se revalida al reintentar con los datos de ese momento.
 *
 * <p>Si el fitosanitario y el fertilizante corresponden el mismo día, va primero el fitosanitario y el fertilizante
 * espera al día siguiente (P-D2). Una aplicación también se difiere si el sector ya está dosificando.
 *
 * <p>El resultado de cada pedido queda en el historial del sector como Registro de Inacción cuando no se aplicó, con
 * el motivo, sin repetirse mientras el motivo no cambie.
 */
@Service
public class ApplicationService implements ConsumidorParametros {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    public static final String NAME = "ApplicationService";
    private static final double MS_PER_HOUR = 3_600_000.0;
    private static final long MS_PER_DAY = 24L * 3_600_000L;

    private final ApplicationRepository applications;
    private final PendingApplicationRepository pending;
    private final DiagnosticoRepository diagnoses;
    private final SectorRepository sectors;
    private final HistorialRepository historyRepository;
    private final HistorialService history;
    private final DespachoRiego irrigation;
    private final WeatherService weather;
    private final ApplicationPreconditions preconditions;
    private final TankService tanks;
    private final FungicideCap fungicideCap;
    private final EffectivenessBlockService effectivenessBlocks;
    private final EffectivenessService effectiveness;
    private final ComandoActuadorPublisher publisher;
    private final RuleOverrides overrides;
    private final DeclaredParameters params;
    private final CatalogoParametrosService catalog;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ApplicationService(ApplicationRepository applications,
                              PendingApplicationRepository pending,
                              DiagnosticoRepository diagnoses,
                              SectorRepository sectors,
                              HistorialRepository historyRepository,
                              HistorialService history,
                              DespachoRiego irrigation,
                              WeatherService weather,
                              ApplicationPreconditions preconditions,
                              TankService tanks,
                              FungicideCap fungicideCap,
                              EffectivenessBlockService effectivenessBlocks,
                              EffectivenessService effectiveness,
                              ComandoActuadorPublisher publisher,
                              RuleOverrides overrides,
                              // Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros) para armarse.
                              @Lazy CatalogoParametrosService catalog,
                              Clock clock,
                              PlatformTransactionManager transactions) {
        this.applications = applications;
        this.pending = pending;
        this.diagnoses = diagnoses;
        this.sectors = sectors;
        this.historyRepository = historyRepository;
        this.history = history;
        this.irrigation = irrigation;
        this.weather = weather;
        this.preconditions = preconditions;
        this.tanks = tanks;
        this.fungicideCap = fungicideCap;
        this.effectivenessBlocks = effectivenessBlocks;
        this.effectiveness = effectiveness;
        this.publisher = publisher;
        this.overrides = overrides;
        this.catalog = catalog;
        this.params = new DeclaredParameters(this, catalog::vigentes);
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosInsumo.FERTILIZER_DOSE, ParametrosInsumo.FUNGICIDE_DOSE,
                ParametrosInsumo.RAIN_BLOCKS_HOURS, ParametrosInsumo.REENTRY_HOURS, ParametrosInsumo.PENDING_EXPIRY_DAYS,
                ParametrosInsumo.FUNGICIDE_CAP_DAYS,
                ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS);
    }

    // -------------------------------------------------------------------------
    // Pedido
    // -------------------------------------------------------------------------

    /**
     * Pide aplicar {@code supply} en {@code sector}. Corre en la transacción de quien llama (el motor, al evaluar el
     * sector): la aplicación, el tanque y la cola se confirman o se revierten con ella.
     *
     * @param originRule        regla que decidió aplicar, para el registro
     * @param requiresDiagnosis {@code true} si la dispara el diagnóstico de IA: exige P-D1 y la vuelve a verificar al reintentar
     */
    @Transactional
    public ApplicationOutcome request(SectorEntity sector, Supply supply, String originRule, boolean requiresDiagnosis) {
        ApplicationOutcome outcome = attempt(sector, supply, originRule, requiresDiagnosis);
        // El diagnóstico que originó el pedido es el que el sector tiene ahora; si queda en cola viaja con ella.
        Long diagnosisTs = requiresDiagnosis ? sector.getDiagnosisTs() : null;
        settle(sector, supply, originRule, requiresDiagnosis, diagnosisTs, outcome, null);
        return outcome;
    }

    /** La regla de origen retira su pedido (el plan dejó de pedir la aplicación, el diagnóstico quedó viejo). */
    @Transactional
    public void cancelPending(String sectorId, Supply supply) {
        pending.findBySectorIdAndSupply(sectorId, supply).ifPresent(p -> {
            pending.delete(p);
            log.info("Sector {}: la regla {} canceló la aplicación pendiente de {}.", sectorId, p.getRuleName(), supply);
        });
    }

    /**
     * Retira el pedido pendiente de {@code supply} sólo si lo hizo {@code ruleName}: otra regla (el plan de nutrición) puede
     * tener el suyo en la misma fila, y no es de quien corta.
     */
    @Transactional
    public void cancelPending(String sectorId, Supply supply, String ruleName) {
        pending.findBySectorIdAndSupply(sectorId, supply)
                .filter(p -> ruleName.equals(p.getRuleName()))
                .ifPresent(p -> cancelPending(sectorId, supply));
    }

    // -------------------------------------------------------------------------
    // Reintento de las diferidas
    // -------------------------------------------------------------------------

    /**
     * Reintenta las aplicaciones pendientes con los datos de ahora. El fitosanitario de un sector se evalúa antes que su
     * fertilizante. Cada pendiente va en su propia transacción: una que falla no deshace las demás.
     *
     * @return cuántas se aplicaron
     */
    public int processPending() {
        List<PendingApplicationEntity> due = pending.findAllByOrderByCreatedTsAsc().stream()
                .sorted(Comparator.comparing(PendingApplicationEntity::getSectorId)
                        .thenComparingInt(p -> p.getSupply().order()))
                .toList();
        int applied = 0;
        for (PendingApplicationEntity p : due) {
            try {
                Boolean wasApplied = transaction.execute(status -> retry(p.getId()));
                if (Boolean.TRUE.equals(wasApplied)) {
                    applied++;
                }
            } catch (RuntimeException e) {
                log.error("Sector {}: falló el reintento de la aplicación pendiente de {}; se reintenta en el próximo ciclo.",
                        p.getSectorId(), p.getSupply(), e);
            }
        }
        return applied;
    }

    private boolean retry(String pendingId) {
        PendingApplicationEntity p = pending.findById(pendingId).orElse(null);
        if (p == null) {
            return false;   // otro hilo la resolvió entre la lectura y esta transacción
        }
        SectorEntity sector = sectors.findById(p.getSectorId()).orElse(null);
        if (sector == null) {
            pending.delete(p);
            return false;
        }
        long now = clock.millis();
        double expiryDays = params.number(ParametrosInsumo.PENDING_EXPIRY_DAYS);
        if (now - p.getCreatedTs() > expiryDays * MS_PER_DAY) {
            pending.delete(p);
            history.registrarInaccion(sector, ActionType.NOOP_INFO, p.getRuleName(), String.format(
                    "La aplicación de %s venció sin una ventana apta en %.0f días y se descartó.",
                    p.getSupply().label().toLowerCase(), expiryDays));
            return false;
        }
        ApplicationOutcome outcome = attempt(sector, p.getSupply(), p.getRuleName(), p.isRequiresDiagnosis());
        settle(sector, p.getSupply(), p.getRuleName(), p.isRequiresDiagnosis(), p.getDiagnosisTs(), outcome, p);
        return outcome.status() == ApplicationOutcome.Status.APPLIED;
    }

    // -------------------------------------------------------------------------
    // Un intento
    // -------------------------------------------------------------------------

    /** Evalúa y, si corresponde, ejecuta. No toca la cola de pendientes: eso lo resuelve {@link #settle}. */
    private ApplicationOutcome attempt(SectorEntity sector, Supply supply, String originRule, boolean requiresDiagnosis) {
        if (!overrides.isEnabled(ApplicationPreconditions.NAME)) {
            return ApplicationOutcome.rejected("las aplicaciones están apagadas desde el control de demo");
        }
        if (SupplyDoseService.DOSIFICANDO.equals(sector.getActuadorPump())) {
            return ApplicationOutcome.deferred("el sector ya está dosificando");
        }

        Instant now = clock.instant();
        ApplicationFacts facts = facts(sector, supply, now, requiresDiagnosis, originRule);
        PreconditionResult result = preconditions.check(facts, catalog.vigentes(), overrides);
        if (!result.isMet()) {
            if (result.capReached()) {
                fungicideCap.reportReached(sector, result.reason());
            }
            return result.verdict() == PreconditionResult.Verdict.REJECT
                    ? ApplicationOutcome.rejected(result.reason())
                    : ApplicationOutcome.deferred(result.reason());
        }
        if (supply == Supply.FERTILIZER && fungicideGoesFirst(sector, now)) {
            return ApplicationOutcome.deferred("P-D2: hoy corresponde el fitosanitario y va antes que el fertilizante");
        }

        String zoneId = sector.getZona().getId();
        int ml = (int) Math.round(params.number(supply.doseParameter()));
        if (!overrides.isBypassed(ApplicationPreconditions.NAME, ApplicationPreconditions.BYPASS_STOCK)) {
            TankService.Stock stock = tanks.check(zoneId, supply, ml);
            if (stock != TankService.Stock.OK) {
                tanks.reportShortage(zoneId, supply, stock);
                return ApplicationOutcome.rejected(stock == TankService.Stock.UNREGISTERED
                        ? "el tanque de " + supply.label().toLowerCase() + " no está registrado en " + zoneId
                        : "el tanque de " + supply.label().toLowerCase() + " de " + zoneId + " no alcanza para " + ml + " ml");
            }
        }

        ComandoActuadorPublisher.Resultado sent =
                publisher.publicar(zoneId, sector.getId(), "pump", "ON", Map.<String, Object>of("ml", ml));
        if (!sent.publicado()) {
            return ApplicationOutcome.deferred("no se pudo publicar la orden de bomba: " + sent.error());
        }
        execute(sector, supply, originRule, ml, now);
        return ApplicationOutcome.applied();
    }

    /** Los efectos de una aplicación ya ordenada al nodo. */
    private void execute(SectorEntity sector, Supply supply, String originRule, int ml, Instant now) {
        sector.setActuadorPump(SupplyDoseService.DOSIFICANDO);
        if (supply == Supply.FUNGICIDE) {
            sector.setReentryUntil(now.toEpochMilli()
                    + Math.round(params.number(ParametrosInsumo.REENTRY_HOURS) * MS_PER_HOUR));
        }
        applications.save(new ApplicationEntity(UUID.randomUUID().toString(), sector.getId(), sector.getZona().getId(),
                supply, ml, now.toEpochMilli(), originRule));
        // E-02, E-03: el tratamiento abre su seguimiento de efectividad con el estado en que estaba el sector.
        FollowUp followUp = effectiveness.followUpFor(sector, supply, originRule, now).orElse(null);
        history.registrarInsumo(sector, ml, supply.label(), originRule, followUp);
        tanks.consume(sector.getZona().getId(), supply, ml);
        log.info("Sector {}: {} ml de {} ordenados al nodo (regla {}).", sector.getId(), ml, supply, originRule);
    }

    /**
     * Cierra el pedido contra la cola: aplicada o rechazada sale de ella, diferida entra o se actualiza. Deja constancia
     * en el historial de lo que no se aplicó, una sola vez mientras el motivo no cambie.
     *
     * <p>El diagnóstico que originó el pedido se da por usado cuando el pedido termina (aplicado o rechazado), no mientras
     * espera: si no, una aplicación diferida perdería el diagnóstico que la justifica (un diagnóstico, una aplicación).
     *
     * @param diagnosisTs {@code diagnosisTs} del diagnóstico de origen, o {@code null} si el pedido no lo disparó uno
     * @param queued      la fila pendiente de la que viene el intento, o {@code null} si es un pedido nuevo
     */
    private void settle(SectorEntity sector, Supply supply, String originRule, boolean requiresDiagnosis,
                        Long diagnosisTs, ApplicationOutcome outcome, PendingApplicationEntity queued) {
        PendingApplicationEntity row = queued != null ? queued
                : pending.findBySectorIdAndSupply(sector.getId(), supply).orElse(null);
        switch (outcome.status()) {
            case APPLIED -> {
                if (row != null) {
                    pending.delete(row);
                }
                useDiagnosis(sector, diagnosisTs);
            }
            case REJECTED -> {
                if (row != null) {
                    pending.delete(row);
                }
                useDiagnosis(sector, diagnosisTs);
                recordNotApplied(sector, supply, originRule, "rechazada", outcome.reason());
            }
            case DEFERRED -> {
                boolean newReason = row == null || !outcome.reason().equals(row.getLastReason());
                if (row == null) {
                    row = new PendingApplicationEntity(UUID.randomUUID().toString(), sector.getId(),
                            sector.getZona().getId(), supply, originRule, requiresDiagnosis, clock.millis(), null,
                            diagnosisTs);
                }
                row.setRuleName(originRule);
                row.setRequiresDiagnosis(requiresDiagnosis);
                row.setDiagnosisTs(diagnosisTs);
                row.setLastReason(outcome.reason());
                pending.save(row);
                if (newReason) {
                    recordNotApplied(sector, supply, originRule, "diferida", outcome.reason());
                }
            }
        }
    }

    /** Da por usado el diagnóstico de origen; nunca retrocede el último usado (un más nuevo ya pudo haberse consumido). */
    private void useDiagnosis(SectorEntity sector, Long diagnosisTs) {
        Long used = sector.getDiagnosisUsadoTs();
        if (diagnosisTs != null && (used == null || diagnosisTs > used)) {
            sector.setDiagnosisUsadoTs(diagnosisTs);
        }
    }

    private void recordNotApplied(SectorEntity sector, Supply supply, String originRule, String what, String reason) {
        history.registrarInaccion(sector, ActionType.NOOP_INFO, originRule, String.format(
                "Aplicación de %s %s: %s.", supply.label().toLowerCase(), what, reason));
    }

    // -------------------------------------------------------------------------
    // Los hechos
    // -------------------------------------------------------------------------

    private ApplicationFacts facts(SectorEntity sector, Supply supply, Instant now, boolean requiresDiagnosis,
                                  String originRule) {
        ZonaEntity zone = sector.getZona();
        WeatherForecast forecast = weather.getForecastSinEspera();

        ValidDiagnosis.Verdict diagnosis = null;
        if (requiresDiagnosis) {
            diagnosis = ValidDiagnosis.of(
                    diagnoses.findFirstBySectorIdOrderByCreadoEnDesc(sector.getId()).orElse(null), now,
                    params.number(ParametrosDiagnostico.CONFIANZA_MINIMA),
                    params.number(ParametrosDiagnostico.VALIDITY_HOURS));
        }
        Double temperature = zone.getTempRaw() != null ? zone.getTempRaw() : (forecast != null ? forecast.tempC() : null);
        WeatherForecast.LluviaPrevista rain = forecast == null ? null : forecast.lluviaProxima(
                LocalDateTime.ofInstant(now, ZonaHorariaVivero.ZONA),
                (int) params.number(ParametrosInsumo.RAIN_BLOCKS_HOURS));

        return new ApplicationFacts(supply, now, diagnosis,
                applications.findFirstBySectorIdAndSupplyOrderByTsDesc(sector.getId(), supply)
                        .map(a -> Instant.ofEpochMilli(a.getTs())).orElse(null),
                applications.findFirstBySectorIdOrderByTsDesc(sector.getId())
                        .map(a -> Instant.ofEpochMilli(a.getTs())).orElse(null),
                temperature, zone.getHumSusRaw(),
                zone.getHumSusTs() == null ? null : Instant.ofEpochMilli(zone.getHumSusTs()),
                lastWatering(sector.getId()), rain, fungicidesInCapWindow(sector, supply, now),
                effectivenessBlocks.supplyBlock(sector, supply, originRule).orElse(null));
    }

    /** Cuántos fitosanitarios cuenta el tope del sector (F-06); sólo el fitosanitario tiene tope. */
    private int fungicidesInCapWindow(SectorEntity sector, Supply supply, Instant now) {
        return supply == Supply.FUNGICIDE
                ? fungicideCap.appliedInWindow(sector, now, params.number(ParametrosInsumo.FUNGICIDE_CAP_DAYS))
                : 0;
    }

    /** El riego más reciente: el del historial o el que el despacho recuerda en memoria si el registro falló. */
    private Instant lastWatering(String sectorId) {
        Long fromHistory = historyRepository.findFirstBySectorIdAndTipoOrderByTsDesc(sectorId, "Riego")
                .map(e -> e.getTs()).orElse(null);
        Long fromDispatch = irrigation.ultimoRiegoMs(sectorId);
        Long latest = fromHistory == null ? fromDispatch
                : (fromDispatch == null ? fromHistory : Long.valueOf(Math.max(fromHistory, fromDispatch)));
        return latest == null ? null : Instant.ofEpochMilli(latest);
    }

    /**
     * P-D2: hay un fitosanitario pendiente en el sector que, con los datos de ahora, se aplicaría. Si está trabado por su
     * propio intervalo o por el clima no frena al fertilizante: esperar eso lo dejaría sin aplicar durante días.
     */
    private boolean fungicideGoesFirst(SectorEntity sector, Instant now) {
        Optional<PendingApplicationEntity> waiting = pending.findBySectorIdAndSupply(sector.getId(), Supply.FUNGICIDE);
        if (waiting.isEmpty()) {
            return false;
        }
        ApplicationFacts fungicideFacts = facts(sector, Supply.FUNGICIDE, now, waiting.get().isRequiresDiagnosis(),
                waiting.get().getRuleName());
        return preconditions.check(fungicideFacts, catalog.vigentes(), overrides).isMet();
    }
}
