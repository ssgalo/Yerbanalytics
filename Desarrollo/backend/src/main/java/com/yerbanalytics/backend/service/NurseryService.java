package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.dto.*;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NurseryService {

    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final HistorialService historialService;
    private final ConfiguracionService configuracionService;
    private final long staleThresholdMs;

    public NurseryService(NurseryProperties properties,
                          ZonaRepository zonaRepository,
                          SectorRepository sectorRepository,
                          HistorialService historialService,
                          ConfiguracionService configuracionService,
                          @Value("${yerbanalytics.nursery.stale-threshold-ms}") long staleThresholdMs) {
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.historialService = historialService;
        this.configuracionService = configuracionService;
        this.staleThresholdMs = staleThresholdMs;
    }

    public NurseryData getSnapshot() {
        List<ZonaEntity> zonesDb = zonaRepository.findAllWithSectors();

        List<Sector> allSectors = new ArrayList<>();
        Map<String, Sector> byId = new LinkedHashMap<>();
        List<Zona> zonas = new ArrayList<>();

        for (ZonaEntity ze : zonesDb) {
            List<Sector> zoneSectors = new ArrayList<>();
            int sano = 0;
            int alerta = 0;
            int off = 0;

            for (SectorEntity se : ze.getSectors()) {
                String finalStatus;
                String finalColor;
                String finalStatusLabel;
                String finalTip;
                List<Metric> metrics;
                Diagnosis diagnosis;
                String reason;
                Actuadores actuators;
                String ago;
                boolean stale;

                boolean isStale = se.getLastReadingTime() == null || 
                        (System.currentTimeMillis() - se.getLastReadingTime() > staleThresholdMs);

                if (isStale) {
                    finalStatus = "offline";
                    finalColor = C.get("offline");
                    finalStatusLabel = LAB.get("offline");
                    finalTip = se.getId() + " · " + finalStatusLabel;
                    metrics = buildOfflineMetricsList();
                    diagnosis = new Diagnosis("Sin diagnóstico", null, EMDASH);
                    reason = "Fuera de servicio";
                    actuators = new Actuadores("Cerrada", "En espera", se.getActuadorShade());
                    ago = se.getLastReadingTime() == null ? "hace —" : formatAgo(se.getLastReadingTime());
                    stale = true;
                } else {
                    finalStatus = se.getStatus();
                    finalColor = se.getColor();
                    finalStatusLabel = se.getStatusLabel();
                    finalTip = se.getTip();
                    metrics = buildMetricsList(se);
                    diagnosis = new Diagnosis(
                            se.getDiagnosisEstado(),
                            se.getDiagnosisConf(),
                            se.getDiagnosisSev()
                    );
                    actuators = new Actuadores(
                            se.getActuadorValve(),
                            se.getActuadorPump(),
                            se.getActuadorShade()
                    );
                    reason = se.getReason();
                    ago = formatAgo(se.getLastReadingTime());
                    stale = false;
                }

                Sector sectorDto = new Sector(
                        se.getId(),
                        ze.getId(),
                        ze.getName(),
                        se.getN(),
                        finalStatus,
                        finalColor,
                        finalStatusLabel,
                        finalTip,
                        metrics,
                        diagnosis,
                        reason,
                        actuators,
                        ago,
                        stale
                );

                zoneSectors.add(sectorDto);
                allSectors.add(sectorDto);
                byId.put(sectorDto.id(), sectorDto);

                if ("ok".equals(finalStatus)) {
                    sano++;
                } else if ("offline".equals(finalStatus)) {
                    off++;
                } else {
                    alerta++;
                }
            }

            // Sort zone sectors by id/n to keep grid order
            zoneSectors.sort(Comparator.comparing(Sector::id));

            zonas.add(new Zona(
                    ze.getId(),
                    ze.getName(),
                    ze.getSub(),
                    zoneSectors,
                    sano,
                    alerta,
                    off,
                    zoneSectors.size()
            ));
        }

        // Sort zones by id to keep top-to-bottom layout
        zonas.sort(Comparator.comparing(Zona::id));

        // Recompute stats
        int totalSano = (int) allSectors.stream().filter(s -> "ok".equals(s.status())).count();
        int totalWarning = (int) allSectors.stream().filter(s -> "warning".equals(s.status())).count();
        int totalCritical = (int) allSectors.stream().filter(s -> "critical".equals(s.status())).count();
        int totalOffline = (int) allSectors.stream().filter(s -> "offline".equals(s.status())).count();
        int totalAlerta = totalWarning + totalCritical;

        // Build diagnoses list based on all updated sectors
        int[] diagCounter = {0};
        List<DiagnosisCard> diagnoses = new ArrayList<>();

        for (Sector s : allSectors) {
            if ("offline".equals(s.status())) continue;
            Diagnosis d = s.diagnosis();
            if ("Sano".equals(d.estado()) || "Sin diagnóstico".equals(d.estado())) {
                continue;
            }
            diagCounter[0]++;
            diagnoses.add(new DiagnosisCard(
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
                    d.conf() != null && d.conf() >= 85
            ));
        }

        // Add some non-conclusive cards for warning sectors
        List<Sector> ncSectors = allSectors.stream()
                .filter(s -> "warning".equals(s.status()))
                .limit(3)
                .toList();
        for (Sector s : ncSectors) {
            diagCounter[0]++;
            diagnoses.add(new DiagnosisCard(
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

        diagnoses.sort(Comparator.comparing(DiagnosisCard::time));

        Stats stats = new Stats(
                600, totalSano, totalWarning, totalCritical, totalOffline, totalAlerta,
                (double) Math.round((totalSano / 600.0) * 100), 41, 28, 7, 6, diagnoses.size()
        );

        Map<String, DiagnosisCard> diagById = new LinkedHashMap<>();
        for (DiagnosisCard d : diagnoses) {
            diagById.put(d.id(), d);
        }

        List<DiagnosisCard> recentDiag = diagnoses.stream()
                .filter(d -> !"No concluyente".equals(d.estado()))
                .limit(5)
                .toList();

        // Recompute priority list
        Map<String, Integer> priorityOrder = Map.of("critical", 0, "warning", 1);
        List<PriorityItem> priority = allSectors.stream()
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

        // Re-build action event list based on alert sectors
        List<Sector> actSrc = allSectors.stream()
                .filter(s -> !"ok".equals(s.status()) && !"offline".equals(s.status()))
                .toList();
        List<ActionEvent> actions = new ArrayList<>();
        for (int i = 0; i < ACT_TPL.size(); i++) {
            ActTemplate a = ACT_TPL.get(i);
            Sector sec = actSrc.isEmpty() ? null : actSrc.get(i % actSrc.size());
            var meta = ACT.get(a.tipo());
            var rm = RES_MAP.get(a.res());
            actions.add(new ActionEvent(
                    a.title() + " · " + (sec != null ? sec.id() : "MZ-2-014"),
                    a.detail(),
                    a.time(),
                    a.res(),
                    rm.soft(),
                    rm.ink(),
                    meta.tint(),
                    meta.ink(),
                    meta.path()
            ));
        }

        // Re-build alerts list
        List<Alert> alerts = List.of(
                new Alert("CRITICAL", C.get("critical"), "14:08",
                        priority.isEmpty() ? "MZ-3-077" : priority.get(0).id(),
                        "Daño fúngico confirmado + humedad de sustrato 84%. Dosificación de fungicida en curso.", false),
                new Alert("CRITICAL", C.get("critical"), "13:41", "MZ-5-042",
                        "Falla hidráulica: caudalímetro sin flujo tras abrir electroválvula. Sector marcado para revisión.", false),
                new Alert("WARNING", C.get("warning"), "13:20", "MZ-2-091",
                        "Nodo testigo con batería baja (18%). Recambio preventivo sugerido.", false),
                new Alert("WARNING", C.get("warning"), "12:55",
                        priority.size() > 2 ? priority.get(2).id() : "MZ-1-033",
                        "Clorosis detectada (confianza 88%). A la espera de validación de dosis nutricional.", false),
                new Alert("WARNING", C.get("warning"), "11:30", "MZ-4-005",
                        "Sensor sin reporte hace 2 h — señal intermitente. Mostrando último dato conocido.", false)
        );

        Weather weather = new Weather(
                21.0,
                "Parcial nublado",
                78.0,
                7.0,
                "Alto",
                "Lluvia probable en ~3 h — riego autónomo pospuesto en 2 macro-zonas.",
                List.of(
                        new ForecastSlot("15 h", 7.0, 10.0),
                        new ForecastSlot("18 h", 3.0, 60.0),
                        new ForecastSlot("21 h", 0.0, 80.0),
                        new ForecastSlot("Mañana", 6.0, 25.0)
                )
        );

        return new NurseryData(
                zonas,
                allSectors,
                byId,
                stats,
                priority,
                diagnoses,
                diagById,
                recentDiag,
                actions,
                alerts,
                weather,
                new HashMap<>(SEV_MAP),
                new HashMap<>(TINTS),
                configuracionService.getEffectiveSpecs()
        );
    }

    @Transactional
    public void updateTelemetry(String zoneId, MqttTelemetryPayload payload) {
        List<SectorEntity> sectors = sectorRepository.findByZonaId(zoneId);
        if (sectors.isEmpty()) {
            return;
        }

        for (SectorEntity s : sectors) {
            String oldStatus = s.getStatus();

            // Update raw readings
            s.setHumSusRaw(payload.metrics().humSus());
            s.setHumAmbRaw(payload.metrics().humAmb());
            s.setTempRaw(payload.metrics().temp());
            s.setCeRaw(payload.metrics().ce());
            s.setUvRaw(payload.metrics().uv());
            s.setLastReadingTime(payload.timestamp());

            // Build temporary metrics list to recompute status
            boolean hasCritical = false;
            boolean hasWarning = false;
            List<Metric> tempMetrics = buildMetricsList(s);
            for (Metric m : tempMetrics) {
                if ("critical".equals(m.status())) {
                    hasCritical = true;
                } else if ("warning".equals(m.status())) {
                    hasWarning = true;
                }
            }

            String finalStatus = hasCritical ? "critical" : (hasWarning ? "warning" : "ok");
            s.setStatus(finalStatus);
            s.setColor(C.get(finalStatus));
            s.setStatusLabel(LAB.get(finalStatus));
            s.setTip(s.getId() + " · " + LAB.get(finalStatus));

            // Diagnosis
            if ("ok".equals(finalStatus)) {
                s.setDiagnosisEstado("Sano");
                s.setDiagnosisConf(98.0);
                s.setDiagnosisSev(EMDASH);
            } else {
                // Keep original if it was already warning/critical and has a valid diagnosis
                if (!"ok".equals(oldStatus) && !"offline".equals(oldStatus) && !"Sin diagnóstico".equals(s.getDiagnosisEstado())) {
                    // keep existing diagnosis
                } else {
                    List<PathoEntry> pList = PATHOS.get(finalStatus);
                    if (pList != null && !pList.isEmpty()) {
                        PathoEntry p = pList.get(0); // Pick default first one
                        s.setDiagnosisEstado(p.estado());
                        s.setDiagnosisConf(92.0);
                        s.setDiagnosisSev(p.sev());
                    }
                }
            }

            // Reason
            Metric bad = tempMetrics.stream()
                    .filter(m -> finalStatus.equals(m.status()))
                    .findFirst()
                    .orElseGet(() -> tempMetrics.stream().filter(m -> !"ok".equals(m.status())).findFirst().orElse(null));
            if (bad != null) {
                s.setReason(bad.label() + " " + bad.value() + ("%".equals(bad.unit()) ? "%" : " " + bad.unit()));
            } else {
                s.setReason(s.getDiagnosisEstado());
            }

            // Actuators
            String oldValve = s.getActuadorValve();
            String oldPump = s.getActuadorPump();
            double humSusVal = s.getHumSusRaw() != null ? s.getHumSusRaw() : 50.0;
            String valve = humSusVal < configuracionService.getRiegoHumSusUmbral() ? "Regando" : "Cerrada";
            String pump = "critical".equals(finalStatus) && s.getDiagnosisConf() != null && s.getDiagnosisConf() >= 85
                    ? "Dosificando" : "En espera";
            s.setActuadorValve(valve);
            s.setActuadorPump(pump);

            // Hook de historial: registrar SÓLO en la transición a estado activo,
            // para no inundar la tabla en cada ciclo de telemetría.
            if ("Regando".equals(valve) && !"Regando".equals(oldValve)) {
                historialService.registrarRiego(s);
            }
            if ("Dosificando".equals(pump) && !"Dosificando".equals(oldPump)) {
                historialService.registrarInsumo(s);
            }
        }
        sectorRepository.saveAll(sectors);
    }

    private List<Metric> buildMetricsList(SectorEntity s) {
        List<Metric> metrics = new ArrayList<>();
        for (MetricSpec sp : configuracionService.getEffectiveSpecs()) {
            Double val = null;
            if ("humSus".equals(sp.key())) {
                val = s.getHumSusRaw();
            } else if ("humAmb".equals(sp.key())) {
                val = s.getHumAmbRaw();
            } else if ("temp".equals(sp.key())) {
                val = s.getTempRaw();
            } else if ("ce".equals(sp.key())) {
                val = s.getCeRaw();
            } else if ("uv".equals(sp.key())) {
                val = s.getUvRaw();
            }

            if (val == null) {
                metrics.add(new Metric(
                        sp.key(),
                        sp.label(),
                        sp.unit(),
                        null,
                        "—",
                        "offline",
                        C.get("offline"),
                        sp
                ));
            } else {
                String status;
                if (val < sp.warn()[0] || val > sp.warn()[1]) {
                    status = "critical";
                } else if (val < sp.ideal()[0] || val > sp.ideal()[1]) {
                    status = "warning";
                } else {
                    status = "ok";
                }

                String valueStr = String.format(Locale.US, "%." + sp.dec() + "f", val);
                metrics.add(new Metric(
                        sp.key(),
                        sp.label(),
                        sp.unit(),
                        val,
                        valueStr,
                        status,
                        C.get(status),
                        sp
                ));
            }
        }
        return metrics;
    }

    private List<Metric> buildOfflineMetricsList() {
        List<Metric> metrics = new ArrayList<>();
        for (MetricSpec sp : configuracionService.getEffectiveSpecs()) {
            metrics.add(new Metric(
                    sp.key(),
                    sp.label(),
                    sp.unit(),
                    null,
                    "—",
                    "offline",
                    C.get("offline"),
                    sp
            ));
        }
        return metrics;
    }

    private String formatAgo(long timestamp) {
        long diffMs = System.currentTimeMillis() - timestamp;
        if (diffMs < 0) diffMs = 0;
        long diffSec = diffMs / 1000;
        if (diffSec < 60) {
            return "hace " + diffSec + " s";
        }
        long diffMin = diffSec / 60;
        return "hace " + diffMin + " min";
    }
}
