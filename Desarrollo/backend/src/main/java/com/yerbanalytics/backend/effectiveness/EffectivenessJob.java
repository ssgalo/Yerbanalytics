package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HardwareService;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recorre los seguimientos de efectividad abiertos del historial y los resuelve (reglas_v2 §9):
 * <ul>
 *   <li><b>E-01</b>: a los {@code efectividad.verificacion-min} del cierre de un riego pide una lectura de verificación a la
 *       macro-zona y compara la humedad de sustrato con la previa al riego;</li>
 *   <li><b>E-02 / E-03</b>: pasada la espera, compara el diagnóstico del sector con el de cuando se aplicó el tratamiento;</li>
 *   <li><b>F-05</b>: un sector que vuelve a Sano antes de la espera cierra su seguimiento como efectivo.</li>
 * </ul>
 *
 * <p>Lo que decide es el veredicto; qué hacer con un «sin efectividad» (bloquear la repetición, S-06) lo decide
 * {@code IneffectiveActionRule} leyendo la marca de bloqueo que este job deja en el evento. El historial de la acción no se
 * edita: sólo se completa el seguimiento que la acción abrió.
 *
 * <p>Corre en su propio scheduler (cada {@code yerbanalytics.historial.eval-interval-ms}), no dentro del ciclo de reglas: antes
 * lo disparaba una regla por cada sector evaluado.
 */
@Component
public class EffectivenessJob {

    private static final Logger log = LoggerFactory.getLogger(EffectivenessJob.class);

    private static final String VERDICT_EFFECTIVE = "Efectiva";
    private static final String VERDICT_INEFFECTIVE = "Sin efectividad";

    private final EffectivenessService policy;
    private final HistorialRepository history;
    private final SectorRepository sectors;
    private final ZonaRepository zones;
    private final HardwareService hardware;
    private final ComandoZonaPublisher publisher;
    private final VerificationReads verification;
    private final SectorNeighbors neighbors;
    private final HistorialService historial;
    private final Clock clock;

    private final IrrigationEvaluator irrigation = new IrrigationEvaluator();
    private final CorrectiveFertilizerEvaluator fertilizer = new CorrectiveFertilizerEvaluator();
    private final FungicideEvaluator fungicide = new FungicideEvaluator();

    public EffectivenessJob(EffectivenessService policy,
                            HistorialRepository history,
                            SectorRepository sectors,
                            ZonaRepository zones,
                            HardwareService hardware,
                            ComandoZonaPublisher publisher,
                            VerificationReads verification,
                            SectorNeighbors neighbors,
                            HistorialService historial,
                            Clock clock) {
        this.policy = policy;
        this.history = history;
        this.sectors = sectors;
        this.zones = zones;
        this.hardware = hardware;
        this.publisher = publisher;
        this.verification = verification;
        this.neighbors = neighbors;
        this.historial = historial;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${yerbanalytics.historial.eval-interval-ms:30000}")
    @Transactional
    public void evaluatePending() {
        if (!policy.isOn()) {
            return;
        }
        migrateLegacyFollowUps();

        long now = clock.millis();
        List<HistorialEventoEntity> resolved = new ArrayList<>();
        Map<String, List<HistorialEventoEntity>> irrigationByZone = new LinkedHashMap<>();
        for (HistorialEventoEntity event : history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNotNull()) {
            Optional<EffectivenessKind> kind = EffectivenessKind.parse(event.getEfectividadTipo());
            if (kind.isEmpty()) {
                continue;
            }
            if (kind.get() == EffectivenessKind.IRRIGATION) {
                irrigationByZone.computeIfAbsent(event.getZonaId(), z -> new ArrayList<>()).add(event);
            } else if (evaluateTreatment(event, kind.get(), now)) {
                resolved.add(event);
            }
        }
        irrigationByZone.forEach((zoneId, events) -> resolved.addAll(evaluateIrrigation(zoneId, events, now)));

        if (!resolved.isEmpty()) {
            history.saveAll(resolved);
        }
    }

    // ------------------------------------------------------------------
    // E-01 · riego
    // ------------------------------------------------------------------

    /** Resuelve los riegos vencidos de una macro-zona con UNA lectura de verificación; devuelve los que cambiaron. */
    private List<HistorialEventoEntity> evaluateIrrigation(String zoneId, List<HistorialEventoEntity> events, long now) {
        List<HistorialEventoEntity> changed = new ArrayList<>();
        Optional<String> witness = hardware.witnessSector(zoneId);
        List<HistorialEventoEntity> due = new ArrayList<>();
        for (HistorialEventoEntity event : events) {
            if (witness.isPresent() && !witness.get().equals(event.getSectorId())) {
                // Sólo se verifica el riego del sector del nodo testigo: el resto no tiene dónde medirse.
                event.setEvoShow(false);
                changed.add(event);
            } else if (now >= irrigationDueAt(event)) {
                due.add(event);
            }
        }
        if (due.isEmpty()) {
            return changed;
        }

        Optional<ZonaEntity> zone = zones.findById(zoneId);
        if (zone.isEmpty()) {
            return changed;
        }
        var answeredAfter = verification.answeredAfter(zoneId);
        boolean usable = answeredAfter.isPresent() && zone.get().getHumSusTs() != null
                && zone.get().getHumSusTs() >= answeredAfter.getAsLong() && zone.get().getHumSusRaw() != null;
        if (!usable) {
            requestVerification(zoneId, now);
            return changed;
        }

        double humidity = zone.get().getHumSusRaw();
        double minDelta = policy.irrigationDelta();
        for (HistorialEventoEntity event : due) {
            double before = event.getValorAntes() != null ? event.getValorAntes() : 0.0;
            close(event, irrigation.evaluate(before, humidity, minDelta), now);
            changed.add(event);
        }
        verification.clear(zoneId);
        return changed;
    }

    /** Cuándo vence un riego: la espera de la verificación corre desde que cierra la válvula, no desde la orden. */
    private long irrigationDueAt(HistorialEventoEntity event) {
        long valveMs = event.getDuracionSeg() == null ? 0 : event.getDuracionSeg() * 1000L;
        return event.getTs() + valveMs + policy.latencyMs(EffectivenessKind.IRRIGATION);
    }

    private void requestVerification(String zoneId, long now) {
        if (!verification.needsRequest(zoneId, now)) {
            return;
        }
        ComandoZonaPublisher.Resultado result = publisher.leerAhora(zoneId);
        if (result.publicado()) {
            verification.expect(zoneId, now);
        } else {
            log.warn("Zona {}: no se pudo pedir la lectura de verificación del riego ({}); se reintenta.", zoneId, result.error());
        }
    }

    // ------------------------------------------------------------------
    // E-02 / E-03 · tratamientos, y F-05
    // ------------------------------------------------------------------

    /** {@code true} si el evento cambió (se cerró el seguimiento). */
    private boolean evaluateTreatment(HistorialEventoEntity event, EffectivenessKind kind, long now) {
        SectorEntity sector = sectors.findById(event.getSectorId()).orElse(null);
        if (sector == null || event.getDiagEstadoAntes() == null) {
            return false;
        }
        Instant instant = Instant.ofEpochMilli(now);
        DiagnosisView current = policy.diagnosisOf(sector, instant);

        // F-05: el sector se curó antes de la espera. El diagnóstico tiene que ser posterior al tratamiento.
        if (current.isHealthy() && sector.getDiagnosisTs() != null && sector.getDiagnosisTs() > event.getTs()) {
            close(event, Evaluation.effective(DiagnosisView.HEALTHY, "Sano",
                    "El sector volvió a Sano después del tratamiento, antes de cumplirse la espera."), now);
            historial.registrarAlerta(event.getZonaId(), event.getZonaName(), EffectivenessService.NAME,
                    new DetalleAlerta(NivelAlerta.INFO, "El sector " + event.getSectorId() + " volvió a Sano después de su "
                            + kind.label().toLowerCase() + ": el seguimiento se cierra como efectivo."), now);
            return true;
        }

        long dueAt = event.getTs() + policy.latencyMs(kind);
        if (now < dueAt) {
            return false;
        }

        TreatmentBaseline baseline = baselineOf(event);
        Evaluation result = kind == EffectivenessKind.CORRECTIVE_FERTILIZER
                ? fertilizer.evaluate(baseline, current)
                : fungicide.evaluate(baseline, current, affectedNeighbors(sector, baseline, instant));
        if (result.isPending()) {
            if (now < dueAt + policy.noDiagnosisGraceMs()) {
                return false;   // se espera un diagnóstico vigente durante la gracia
            }
            result = Evaluation.ineffective("—", "sin dato",
                    "Sin efectividad por falta de dato: venció la espera y el sector no tuvo un diagnóstico vigente (" + result.reason() + ").");
        }
        close(event, result, now);
        return true;
    }

    private Set<String> affectedNeighbors(SectorEntity sector, TreatmentBaseline baseline, Instant now) {
        return neighbors.affectedAround(sector, neighbor -> policy.hasSameDiagnosis(neighbor, baseline.state(), now));
    }

    private static TreatmentBaseline baselineOf(HistorialEventoEntity event) {
        Set<String> neighborIds = event.getVecinosAntes() == null || event.getVecinosAntes().isBlank()
                ? Set.of()
                : new HashSet<>(Arrays.asList(event.getVecinosAntes().split(",")));
        return new TreatmentBaseline(event.getDiagEstadoAntes(), Severity.parse(event.getSev()).orElse(null), neighborIds);
    }

    // ------------------------------------------------------------------

    private void close(HistorialEventoEntity event, Evaluation result, long now) {
        boolean effective = result.outcome() == Evaluation.Outcome.EFFECTIVE;
        event.setEvoAhora(result.now());
        event.setEvoDelta(result.delta());
        event.setEvoVerdict(effective ? VERDICT_EFFECTIVE : VERDICT_INEFFECTIVE);
        event.setEvoMotivo(result.reason());
        event.setEvoEvaluadoTs(now);
        event.setBloqueoRepeticion(!effective);
        event.setRes(VERDICT_EFFECTIVE);
        if (!effective) {
            warnIneffective(event, result, now);
        }
    }

    /**
     * S-06: una acción sin efectividad bloquea su repetición hasta que alguien la revise en persona, y se avisa una sola vez, en el
     * momento del veredicto, a la macro-zona. El bloqueo mismo lo aplican {@code IneffectiveActionRule} (riego) y las
     * precondiciones de aplicación (tratamientos).
     */
    private void warnIneffective(HistorialEventoEntity event, Evaluation result, long now) {
        EffectivenessKind kind = EffectivenessKind.parse(event.getEfectividadTipo()).orElse(EffectivenessKind.IRRIGATION);
        String scope = kind.isZoneScoped()
                ? "en toda la macro-zona " + event.getZonaId()
                : "en el sector " + event.getSectorId();
        historial.registrarAlerta(event.getZonaId(), event.getZonaName(), EffectivenessService.NAME,
                new DetalleAlerta(NivelAlerta.CRITICAL, kind.label() + " sin efectividad (" + result.reason()
                        + ") Se bloquea su repetición autónoma " + scope + " hasta que se registre la revisión física."), now);
    }

    /**
     * Los seguimientos abiertos de antes de la evaluación v2 no tienen tipo: los riegos pasan a evaluarse como tales; los demás
     * (insumos, que se seguían por la humedad) dejan de ser un seguimiento, porque esa comparación ya no existe.
     */
    private void migrateLegacyFollowUps() {
        List<HistorialEventoEntity> legacy = history.findByEvoShowTrueAndEvoEvaluadoTsIsNullAndEfectividadTipoIsNull();
        if (legacy.isEmpty()) {
            return;
        }
        for (HistorialEventoEntity event : legacy) {
            if ("Riego".equals(event.getTipo())) {
                event.setEfectividadTipo(EffectivenessKind.IRRIGATION.name());
            } else {
                event.setEvoShow(false);
            }
        }
        history.saveAll(legacy);
    }
}
