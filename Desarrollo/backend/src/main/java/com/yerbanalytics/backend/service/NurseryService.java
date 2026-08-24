package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.dto.*;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.TopologiaLayoutEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.BloqueoManualRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NurseryService {

    /** Identidad de la fila única de disposición visual. */
    private static final int LAYOUT_ID = 1;

    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final HistorialService historialService;
    private final ConfiguracionService configuracionService;
    private final HardwareService hardwareService;
    private final TopologiaLayoutRepository layoutRepository;
    private final RuleOrchestrator ruleOrchestrator;
    private final ActionExecutor actionExecutor;
    private final WeatherService weatherService;
    private final BloqueoManualRepository bloqueoManualRepository;
    private final DiagnosticoService diagnosticoService;
    private final long staleThresholdMs;
    private final int bateriaMinPct;

    public NurseryService(NurseryProperties properties,
                          ZonaRepository zonaRepository,
                          SectorRepository sectorRepository,
                          HistorialService historialService,
                          ConfiguracionService configuracionService,
                          HardwareService hardwareService,
                          TopologiaLayoutRepository layoutRepository,
                          RuleOrchestrator ruleOrchestrator,
                          ActionExecutor actionExecutor,
                          WeatherService weatherService,
                          BloqueoManualRepository bloqueoManualRepository,
                          DiagnosticoService diagnosticoService,
                          @Value("${yerbanalytics.nursery.stale-threshold-ms}") long staleThresholdMs,
                          @Value("${yerbanalytics.hardware.bateria-min-pct:20}") int bateriaMinPct) {
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.historialService = historialService;
        this.configuracionService = configuracionService;
        this.hardwareService = hardwareService;
        this.layoutRepository = layoutRepository;
        this.ruleOrchestrator = ruleOrchestrator;
        this.actionExecutor = actionExecutor;
        this.weatherService = weatherService;
        this.bloqueoManualRepository = bloqueoManualRepository;
        this.diagnosticoService = diagnosticoService;
        this.staleThresholdMs = staleThresholdMs;
        this.bateriaMinPct = bateriaMinPct;
    }

    public NurseryData getSnapshot() {
        List<ZonaEntity> zonesDb = zonaRepository.findAllWithSectors();

        List<Sector> allSectors = new ArrayList<>();
        Map<String, Sector> byId = new LinkedHashMap<>();
        List<Zona> zonas = new ArrayList<>();
        // Antigüedad de la lectura por zona: los diagnósticos la usan como sello temporal,
        // y ya no vive en el sector.
        Map<String, String> agoPorZona = new HashMap<>();

        for (ZonaEntity ze : zonesDb) {
            List<Sector> zoneSectors = new ArrayList<>();
            int sano = 0;
            int alerta = 0;
            int off = 0;

            // La lectura es de la zona: un solo nodo testigo la produce y sus 100 sectores
            // la comparten. Si el nodo dejó de reportar, toda la zona queda fuera de servicio.
            boolean isStale = ze.getLastReadingTime() == null
                    || (System.currentTimeMillis() - ze.getLastReadingTime() > staleThresholdMs);
            List<Metric> zoneMetrics = isStale ? buildOfflineMetricsList() : buildMetricsList(ze);
            String zoneAgo = ze.getLastReadingTime() == null ? "hace —" : formatAgo(ze.getLastReadingTime());
            agoPorZona.put(ze.getId(), zoneAgo);

            for (SectorEntity se : ze.getSectors()) {
                String finalStatus;
                String finalColor;
                String finalStatusLabel;
                String finalTip;
                Diagnosis diagnosis;
                String reason;
                Actuadores actuators;

                if (isStale) {
                    finalStatus = "offline";
                    finalColor = C.get("offline");
                    finalStatusLabel = LAB.get("offline");
                    finalTip = se.getId() + " · " + finalStatusLabel;
                    diagnosis = new Diagnosis(
                            se.getDiagnosisEstado(),
                            se.getDiagnosisConf(),
                            se.getDiagnosisSev()
                    );
                    reason = "Fuera de servicio";
                    actuators = new Actuadores("Cerrada", "En espera", se.getActuadorShade());
                } else {
                    finalStatus = se.getStatus();
                    finalColor = se.getColor();
                    finalStatusLabel = se.getStatusLabel();
                    finalTip = se.getTip();
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
                        diagnosis,
                        reason,
                        actuators
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
                    zoneSectors.size(),
                    new LecturaZona(zoneMetrics, ze.getLastReadingTime(), zoneAgo, isStale),
                    new NodoTestigo(
                            ze.getNodoMac(),
                            ze.getNodoBattery(),
                            ze.getNodoSignal(),
                            ze.getNodoBattery() != null && ze.getNodoBattery() < bateriaMinPct
                    )
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
                    agoPorZona.getOrDefault(s.zona(), "hace —"),
                    d.conf() != null && d.conf() >= 85,
                    null // derivada del sector: no tiene captura asociada
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
                    agoPorZona.getOrDefault(s.zona(), "hace —"),
                    false,
                    null // derivada del sector: no tiene captura asociada
            ));
        }

        diagnoses.sort(Comparator.comparing(DiagnosisCard::time));

        // Diagnósticos persistidos (los que nacieron de una captura real) al frente de la
        // lista: son los más recientes y los únicos con fotografía. La vista no los distingue
        // de los derivados — así, cuando el modelo emita el diagnóstico en lugar del operario,
        // el dashboard no cambia en absoluto.
        diagnoses.addAll(0, cardsPersistidas());

        int totalSectores = allSectors.size();
        double pctSano = totalSectores > 0 ? Math.round((totalSano / (double) totalSectores) * 100) : 0;
        Stats stats = new Stats(
                totalSectores, totalSano, totalWarning, totalCritical, totalOffline, totalAlerta,
                pctSano, 41, 28, 7, 6, diagnoses.size()
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

        // Disposición visual configurada, acotada a la grilla actual (HU-18 CA-01).
        int macroZonas = zonas.size();
        int sectoresPorMacroZona = macroZonas > 0 ? totalSectores / macroZonas : 0;
        LayoutTopologia layout = buildLayout(macroZonas, sectoresPorMacroZona);

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
                configuracionService.getEffectiveSpecs(),
                layout
        );
    }

    /** Disposición visual de la fila única (defaults si nunca se configuró), acotada a la grilla. */
    private LayoutTopologia buildLayout(int macroZonas, int sectoresPorMacroZona) {
        TopologiaLayoutEntity e = layoutRepository.findById(LAYOUT_ID).orElse(null);
        int mzPorFila = e != null ? e.getMacroZonasPorFila() : 3;
        int secPorFila = e != null ? e.getSectoresPorFila() : 10;
        return new LayoutTopologia(clampLayout(mzPorFila, macroZonas), clampLayout(secPorFila, sectoresPorMacroZona));
    }

    private static int clampLayout(int value, int max) {
        if (value < 1) {
            return 1;
        }
        return max > 0 ? Math.min(value, max) : value;
    }

    @Transactional
    public void updateTelemetry(String zoneId, MqttTelemetryPayload payload) {
        ZonaEntity zona = zonaRepository.findById(zoneId).orElse(null);
        if (zona == null) {
            return;
        }
        List<SectorEntity> sectors = sectorRepository.findByZonaId(zoneId);

        // La lectura se persiste UNA vez, en la zona. Sólo se actualizan las métricas
        // presentes en el payload: una lectura parcial (un sensor en falla, o un envío
        // manual de una sola métrica) conserva el último valor de las demás.
        MqttTelemetryPayload.MetricsPayload pm = payload.metrics();
        if (pm != null) {
            if (pm.humSus() != null) zona.setHumSusRaw(pm.humSus());
            if (pm.humAmb() != null) zona.setHumAmbRaw(pm.humAmb());
            if (pm.temp() != null) zona.setTempRaw(pm.temp());
            if (pm.tempSuelo() != null) zona.setTempSueloRaw(pm.tempSuelo());
            if (pm.uv() != null) zona.setUvRaw(pm.uv());
            // Única conversión de unidad del sistema: el contrato manda µS/cm, la
            // plataforma persiste dS/m (ver ContratoNodo).
            if (pm.ce() != null) zona.setCeRaw(ContratoNodo.ceADsPorM(pm.ce()));
            if (pm.phSuelo() != null) zona.setPhSueloRaw(pm.phSuelo());
            if (pm.n() != null) zona.setNRaw(pm.n());
            if (pm.p() != null) zona.setPRaw(pm.p());
            if (pm.k() != null) zona.setKRaw(pm.k());
        }
        if (payload.mac() != null && !payload.mac().isBlank()) zona.setNodoMac(payload.mac().trim());
        if (payload.battery() != null) zona.setNodoBattery(payload.battery());
        if (payload.signal() != null) zona.setNodoSignal(payload.signal());
        zona.setLastReadingTime(payload.timestamp() != null ? payload.timestamp() : System.currentTimeMillis());
        zonaRepository.save(zona);

        // El estado de cada sector se deriva de esa única lectura. Sólo las métricas no
        // informativas mueven el estado: las de la sonda de suelo tienen rangos
        // provisionales y no deben pintar el mapa de rojo hasta validarlos.
        List<Metric> tempMetrics = buildMetricsList(zona);
        boolean hasCritical = false;
        boolean hasWarning = false;
        for (Metric m : tempMetrics) {
            if (!Boolean.TRUE.equals(m.spec().afectaEstado())) {
                continue;
            }
            if ("critical".equals(m.status())) {
                hasCritical = true;
            } else if ("warning".equals(m.status())) {
                hasWarning = true;
            }
        }
        String finalStatus = hasCritical ? "critical" : (hasWarning ? "warning" : "ok");

        for (SectorEntity s : sectors) {
            String oldStatus = s.getStatus();
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

            // Reason: sólo sobre las métricas que efectivamente movieron el estado, para no
            // justificar un "warning" citando una métrica informativa que no lo causó.
            List<Metric> decisivas = tempMetrics.stream()
                    .filter(m -> Boolean.TRUE.equals(m.spec().afectaEstado()))
                    .toList();
            Metric bad = decisivas.stream()
                    .filter(m -> finalStatus.equals(m.status()))
                    .findFirst()
                    .orElseGet(() -> decisivas.stream().filter(m -> !"ok".equals(m.status())).findFirst().orElse(null));
            if (bad != null) {
                s.setReason(bad.label() + " " + bad.value() + ("%".equals(bad.unit()) ? "%" : " " + bad.unit()));
            } else {
                s.setReason(s.getDiagnosisEstado());
            }

            // Motor de Reglas: delega la decisión de actuación al orquestador.
            // El ActionExecutor materializa las acciones (actualiza actuadores y persiste historial).
            RuleContext ctx = buildRuleContext(s, tempMetrics, finalStatus);
            actionExecutor.execute(ruleOrchestrator.evaluate(ctx), ctx);
        }
        sectorRepository.saveAll(sectors);

        // Heartbeat del nodo testigo de la zona: batería/señal/último update (HU-21 CA-01).
        // Antes el mac/battery del payload se descartaban; ahora alimentan el registro de hardware.
        hardwareService.actualizarHeartbeat(zoneId, payload.mac(), payload.battery(), payload.signal(), payload.timestamp());
    }

    /**
     * Construye el {@link RuleContext} para un sector en el ciclo de evaluación actual.
     *
     * <p>Puebla todos los campos del snapshot:
     * <ul>
     *   <li>{@code sensorStale} — derivado del {@code lastReadingTime} de la zona.</li>
     *   <li>{@code forecast} — obtenido de {@link WeatherService} (puede ser null en modo degradado).</li>
     *   <li>{@code bloqueoManualActivo} — consulta {@link BloqueoManualRepository} por sector y zona.</li>
     * </ul>
     */
    private RuleContext buildRuleContext(SectorEntity s, List<Metric> metrics, String finalStatus) {
        ZonaEntity zona = s.getZona();
        boolean sensorStale = zona == null
                || zona.getLastReadingTime() == null
                || (System.currentTimeMillis() - zona.getLastReadingTime() > staleThresholdMs);

        WeatherForecast forecast = weatherService.getForecast();

        boolean bloqueoActivo = !bloqueoManualRepository.findBySectorIdAndActivoTrue(s.getId()).isEmpty()
                || (zona != null && !bloqueoManualRepository.findByZonaIdAndActivoTrue(zona.getId()).isEmpty());

        return new RuleContext(
                s,
                zona,
                configuracionService.getEffectiveSpecs(),
                metrics,
                configuracionService.getConfiguracionOperativa(),
                finalStatus,
                Instant.now(),
                sensorStale,
                forecast,
                bloqueoActivo
        );
    }

    /** Evalúa la lectura de la macro-zona contra los umbrales vigentes. */
    private List<Metric> buildMetricsList(ZonaEntity z) {
        List<Metric> metrics = new ArrayList<>();
        for (MetricSpec sp : configuracionService.getEffectiveSpecs()) {
            Double val = z.raw(sp.key());

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

    /**
     * Diagnósticos persistidos convertidos a tarjetas de la vista.
     *
     * <p>Estos son los que nacieron de una captura real y traen {@code imagenUrl}. Comparten
     * lista con los que se derivan del estado del sector, y la vista no puede distinguirlos:
     * es la propiedad que hace que enchufar el modelo de IA no requiera tocar el frontend.
     *
     * <p>Sus identificadores usan el prefijo {@code DX-} justamente para no colisionar con el
     * {@code DG-###} sintético, porque ambos conjuntos alimentan el mismo índice
     * {@code diagById}.
     */
    private List<DiagnosisCard> cardsPersistidas() {
        Map<String, String> nombrePorZona = new HashMap<>();
        zonaRepository.findAll().forEach(z -> nombrePorZona.put(z.getId(), z.getName()));

        List<DiagnosisCard> out = new ArrayList<>();
        for (var e : diagnosticoService.entidadesRecientes()) {
            String sev = e.getSev();
            ColorPair color = SEV_MAP.getOrDefault(sev, SEV_MAP.get(EMDASH));
            out.add(new DiagnosisCard(
                    e.getId(),
                    e.getSectorId(),
                    nombrePorZona.getOrDefault(e.getZonaId(), e.getZonaId()),
                    e.getEstado(),
                    e.getConf(),
                    sev,
                    color.soft(),
                    color.ink(),
                    TINTS.getOrDefault(e.getEstado(), TINTS.get("Sin diagnóstico")),
                    formatAgo(e.getCreadoEn()),
                    diagnosticoService.esConcluyente(e.getConf()),
                    CapturaService.imagenUrl(e.getCapturaId())
            ));
        }
        return out;
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
