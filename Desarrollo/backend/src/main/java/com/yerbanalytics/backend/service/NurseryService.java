package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.dto.*;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.service.mock.NurseryGenerator;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NurseryService {

    private final NurseryGenerator generator;
    private final int seed;
    private NurseryData cache;
    private final ConcurrentHashMap<String, MqttTelemetryPayload> telemetryMap = new ConcurrentHashMap<>();

    public NurseryService(NurseryProperties properties) {
        this.generator = new NurseryGenerator();
        this.seed = properties.getSeed();
    }

    public NurseryData getSnapshot() {
        if (cache == null) {
            cache = generator.build(seed);
        }
        if (telemetryMap.isEmpty()) {
            return cache;
        }
        return mergeTelemetry(cache, telemetryMap);
    }

    public void updateTelemetry(String zoneId, MqttTelemetryPayload payload) {
        telemetryMap.put(zoneId, payload);
    }

    private NurseryData mergeTelemetry(NurseryData base, Map<String, MqttTelemetryPayload> telemetry) {
        List<Zona> updatedZonas = new ArrayList<>();
        List<Sector> updatedSectors = new ArrayList<>();
        Map<String, Sector> updatedById = new LinkedHashMap<>();

        for (Zona z : base.zonas()) {
            MqttTelemetryPayload payload = telemetry.get(z.id());
            if (payload == null) {
                updatedZonas.add(z);
                for (Sector s : z.sectors()) {
                    updatedSectors.add(s);
                    updatedById.put(s.id(), s);
                }
            } else {
                List<Sector> zoneSectors = new ArrayList<>();
                int sano = 0;
                int alerta = 0;
                int off = 0;

                for (Sector s : z.sectors()) {
                    Sector updatedSector = updateSectorWithTelemetry(s, payload);
                    zoneSectors.add(updatedSector);
                    updatedSectors.add(updatedSector);
                    updatedById.put(updatedSector.id(), updatedSector);

                    if ("ok".equals(updatedSector.status())) {
                        sano++;
                    } else if ("offline".equals(updatedSector.status())) {
                        off++;
                    } else {
                        alerta++;
                    }
                }
                updatedZonas.add(new Zona(z.id(), z.name(), z.sub(), zoneSectors, sano, alerta, off, z.total()));
            }
        }

        // Recompute stats
        int totalSano = (int) updatedSectors.stream().filter(s -> "ok".equals(s.status())).count();
        int totalWarning = (int) updatedSectors.stream().filter(s -> "warning".equals(s.status())).count();
        int totalCritical = (int) updatedSectors.stream().filter(s -> "critical".equals(s.status())).count();
        int totalOffline = (int) updatedSectors.stream().filter(s -> "offline".equals(s.status())).count();
        int totalAlerta = totalWarning + totalCritical;

        // Diagnoses: re-build diagnosis list based on all updated sectors
        int[] diagCounter = {0};
        List<DiagnosisCard> updatedDiagnoses = new ArrayList<>();

        for (Sector s : updatedSectors) {
            if ("offline".equals(s.status())) continue;
            Diagnosis d = s.diagnosis();
            if ("Sano".equals(d.estado()) || "Sin diagnóstico".equals(d.estado())) {
                continue;
            }
            diagCounter[0]++;
            updatedDiagnoses.add(new DiagnosisCard(
                    "DG-" + String.format(Locale.US, "%03d", diagCounter[0]),
                    s.id(),
                    s.zonaName(),
                    d.estado(),
                    d.conf(),
                    d.sev(),
                    SEV_MAP.get(d.sev()).soft(),
                    SEV_MAP.get(d.sev()).ink(),
                    TINTS.getOrDefault(d.estado(), TINTS.get("Sin diagnóstico")),
                    s.ago(),
                    d.conf() >= 85
            ));
        }

        // Add some non-conclusive cards for warning sectors to match NurseryGenerator
        List<Sector> ncSectors = updatedSectors.stream()
                .filter(s -> "warning".equals(s.status()))
                .limit(3)
                .toList();
        for (Sector s : ncSectors) {
            diagCounter[0]++;
            updatedDiagnoses.add(new DiagnosisCard(
                    "DG-" + String.format(Locale.US, "%03d", diagCounter[0]),
                    s.id(),
                    s.zonaName(),
                    "No concluyente",
                    75.0,
                    EMDASH,
                    SEV_MAP.get(EMDASH).soft(),
                    SEV_MAP.get(EMDASH).ink(),
                    TINTS.get("No concluyente"),
                    s.ago(),
                    false
            ));
        }

        updatedDiagnoses.sort(Comparator.comparing(DiagnosisCard::time));

        Stats updatedStats = new Stats(
                600, totalSano, totalWarning, totalCritical, totalOffline, totalAlerta,
                (double) Math.round((totalSano / 600.0) * 100), 41, 28, 7, 6, updatedDiagnoses.size()
        );

        Map<String, DiagnosisCard> updatedDiagById = new LinkedHashMap<>();
        for (DiagnosisCard d : updatedDiagnoses) {
            updatedDiagById.put(d.id(), d);
        }

        List<DiagnosisCard> updatedRecentDiag = updatedDiagnoses.stream()
                .filter(d -> !"No concluyente".equals(d.estado()))
                .limit(5)
                .toList();

        // Recompute priority list
        Map<String, Integer> priorityOrder = Map.of("critical", 0, "warning", 1);
        List<PriorityItem> updatedPriority = updatedSectors.stream()
                .filter(s -> "critical".equals(s.status()) || "warning".equals(s.status()))
                .sorted(Comparator
                        .comparingInt((Sector s) -> priorityOrder.get(s.status()))
                        .thenComparing(Sector::id))
                .limit(7)
                .map(s -> new PriorityItem(
                        s.id(),
                        s.color(),
                        s.reason(),
                        s.diagnosis().sev(),
                        SEV_MAP.get(s.diagnosis().sev()).soft(),
                        SEV_MAP.get(s.diagnosis().sev()).ink(),
                        "critical".equals(s.status()) ? "ybPulse 2s infinite" : "none"
                ))
                .toList();

        return new NurseryData(
                updatedZonas,
                updatedSectors,
                updatedById,
                updatedStats,
                updatedPriority,
                updatedDiagnoses,
                updatedDiagById,
                updatedRecentDiag,
                base.actions(),
                base.alerts(),
                base.weather(),
                base.sevMap(),
                base.tints(),
                base.specs()
        );
    }

    private Sector updateSectorWithTelemetry(Sector original, MqttTelemetryPayload payload) {
        List<Metric> updatedMetrics = new ArrayList<>();
        boolean hasCritical = false;
        boolean hasWarning = false;

        for (Metric m : original.metrics()) {
            Double val = null;
            if ("humSus".equals(m.key())) {
                val = payload.metrics().humSus();
            } else if ("humAmb".equals(m.key())) {
                val = payload.metrics().humAmb();
            } else if ("temp".equals(m.key())) {
                val = payload.metrics().temp();
            } else if ("ce".equals(m.key())) {
                val = payload.metrics().ce();
            } else if ("uv".equals(m.key())) {
                val = payload.metrics().uv();
            }

            if (val == null) {
                updatedMetrics.add(m);
                if ("critical".equals(m.status())) hasCritical = true;
                else if ("warning".equals(m.status())) hasWarning = true;
            } else {
                MetricSpec sp = m.spec();
                String status;
                if (val < sp.warn()[0] || val > sp.warn()[1]) {
                    status = "critical";
                    hasCritical = true;
                } else if (val < sp.ideal()[0] || val > sp.ideal()[1]) {
                    status = "warning";
                    hasWarning = true;
                } else {
                    status = "ok";
                }

                String valueStr = String.format(Locale.US, "%." + sp.dec() + "f", val);
                updatedMetrics.add(new Metric(
                        m.key(),
                        m.label(),
                        m.unit(),
                        val,
                        valueStr,
                        status,
                        C.get(status),
                        sp
                ));
            }
        }

        String finalStatus = hasCritical ? "critical" : (hasWarning ? "warning" : "ok");

        // Determine diagnosis
        Diagnosis diagnosis;
        if ("ok".equals(finalStatus)) {
            diagnosis = new Diagnosis("Sano", 98.0, EMDASH);
        } else {
            // Keep original if it was already warning/critical
            if (!"ok".equals(original.status()) && !"offline".equals(original.status())) {
                diagnosis = original.diagnosis();
            } else {
                List<PathoEntry> pList = PATHOS.get(finalStatus);
                PathoEntry p = pList.get(0); // Pick default first one
                diagnosis = new Diagnosis(p.estado(), 92.0, p.sev());
            }
        }

        // Determine reason
        String reason;
        Metric bad = updatedMetrics.stream()
                .filter(m -> finalStatus.equals(m.status()))
                .findFirst()
                .orElseGet(() -> updatedMetrics.stream().filter(m -> !"ok".equals(m.status())).findFirst().orElse(null));
        if (bad != null) {
            reason = bad.label() + " " + bad.value() + ("%".equals(bad.unit()) ? "%" : " " + bad.unit());
        } else {
            reason = diagnosis.estado();
        }

        Metric humSusMetric = updatedMetrics.stream().filter(m -> "humSus".equals(m.key())).findFirst().orElse(null);
        double humSusVal = humSusMetric != null ? humSusMetric.raw() : 50.0;
        String valve = humSusVal < 42 ? "Regando" : "Cerrada";
        String pump = "critical".equals(finalStatus) && diagnosis.conf() != null && diagnosis.conf() >= 85
                ? "Dosificando" : "En espera";

        Actuadores actuators = new Actuadores(valve, pump, original.actuadores().shade());

        return new Sector(
                original.id(),
                original.zona(),
                original.zonaName(),
                original.n(),
                finalStatus,
                C.get(finalStatus),
                LAB.get(finalStatus),
                original.id() + " \u00b7 " + LAB.get(finalStatus),
                updatedMetrics,
                diagnosis,
                reason,
                actuators,
                "hace 0 min",
                false
        );
    }
}
