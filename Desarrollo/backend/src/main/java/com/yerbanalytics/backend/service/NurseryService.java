package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.dto.*;
import com.yerbanalytics.backend.effectiveness.VerificationReads;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.ResultadoEvaluacion;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.riego.CicloLectura;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.zone.ZoneRuleEngine;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.readings.ReadingCheck;
import com.yerbanalytics.backend.readings.ReadingMetrics;
import com.yerbanalytics.backend.readings.ReadingOrigin;
import com.yerbanalytics.backend.readings.ReadingRecorder;
import com.yerbanalytics.backend.readings.VaporPressureDeficit;
import com.yerbanalytics.backend.readings.ReadingValidationGuard;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.model.DiagnosticoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.TopologiaLayoutEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NurseryService {

    private static final Logger log = LoggerFactory.getLogger(NurseryService.class);

    /**
     * Cuánto del historial mira el contexto de riego: lo más largo que pueden pedir el tope de R-02
     * ({@code riego.exceptuado-bloqueo}, hasta 24 h) y la pausa de R-06 ({@code riego.pausa-tras-aplicacion},
     * hasta 24 h). Un evento más viejo no cambia ninguna decisión.
     */
    private static final long VENTANA_HISTORIAL_RIEGO_MS = 24L * 3_600_000L;

    /** Zonas cuyo nodo ya avisó de un timestamp inutilizable: el warn sale una vez por zona, no por mensaje. */
    private final Set<String> zonasConTimestampInvalido = ConcurrentHashMap.newKeySet();

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
    private final ManualLockRepository manualLockRepository;
    private final DiagnosticoService diagnosticoService;
    private final TrazaEvaluacionStore trazaStore;
    private final HistorialRepository historialRepository;
    private final DespachoRiego despacho;
    /** Opcional (por setter): el estado de la bomba con la fase que informa el nodo ("En cola"). Sin él, el guardado. */
    private SupplyDoseService supplyDose;
    /** Opcional (por setter): las reglas por macro-zona (plan de nutrición). Sin él, la ingesta sólo evalúa sectores. */
    private ZoneRuleEngine zoneRules;
    /** Opcional (por setter): el plan de nutrición que se muestra en el detalle de cada zona. Sin él, el snapshot no lo trae. */
    private NutritionPlanService nutritionPlan;
    /** Opcional (por setter): las lecturas de verificación de E-01, que se guardan sin correr el motor. */
    private VerificationReads verificationReads;
    private final PulsoSensadoService pulsos;
    private final EscalamientoNodoService escalamiento;
    private final ReadingRecorder readings;
    private final ReadingValidationGuard readingGuard;
    private final Clock reloj;
    private final int bateriaMinPct;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSupplyDose(SupplyDoseService supplyDose) {
        this.supplyDose = supplyDose;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setZoneRules(ZoneRuleEngine zoneRules) {
        this.zoneRules = zoneRules;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNutritionPlan(NutritionPlanService nutritionPlan) {
        this.nutritionPlan = nutritionPlan;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setVerificationReads(VerificationReads verificationReads) {
        this.verificationReads = verificationReads;
    }

    private boolean isVerificationReading(String zoneId, long receivedMs) {
        return verificationReads != null && verificationReads.consume(zoneId, receivedMs);
    }

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
                          ManualLockRepository manualLockRepository,
                          DiagnosticoService diagnosticoService,
                          TrazaEvaluacionStore trazaStore,
                          HistorialRepository historialRepository,
                          DespachoRiego despacho,
                          PulsoSensadoService pulsos,
                          EscalamientoNodoService escalamiento,
                          ReadingRecorder readings,
                          ReadingValidationGuard readingGuard,
                          Clock reloj,
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
        this.manualLockRepository = manualLockRepository;
        this.diagnosticoService = diagnosticoService;
        this.trazaStore = trazaStore;
        this.historialRepository = historialRepository;
        this.despacho = despacho;
        this.pulsos = pulsos;
        this.escalamiento = escalamiento;
        this.readings = readings;
        this.readingGuard = readingGuard;
        this.reloj = reloj;
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
        Map<String, Long> lecturaPorZona = new HashMap<>();

        for (ZonaEntity ze : zonesDb) {
            List<Sector> zoneSectors = new ArrayList<>();
            int sano = 0;
            int alerta = 0;
            int off = 0;

            // La lectura es de la zona: un solo nodo testigo la produce y sus 100 sectores
            // la comparten. Si el nodo no respondió a tres pulsos seguidos (o nunca leyó), toda la zona
            // queda fuera de servicio. Entre pulsos el nodo no reporta: el tiempo sin lectura no lo decide.
            boolean isStale = ze.getLastReadingTime() == null || EscalamientoNodoService.fueraDeServicio(ze);
            List<Metric> zoneMetrics = isStale ? buildOfflineMetricsList() : buildMetricsList(ze);
            String zoneAgo = ze.getLastReadingTime() == null ? "hace —" : formatAgo(ze.getLastReadingTime());
            agoPorZona.put(ze.getId(), zoneAgo);
            lecturaPorZona.put(ze.getId(), ze.getLastReadingTime());

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
                            se.getDiagnosisSev(),
                            se.getDiagnosisConfidence()
                    );
                    reason = "Fuera de servicio";
                    actuators = new Actuadores("Cerrada", "En espera", se.getActuadorShade());
                } else {
                    finalStatus = se.getStatus();
                    // El color sale del estado, no de la columna: así una fila guardada con la paleta
                    // anterior se ve con la del sistema de diseño vigente sin esperar a la próxima lectura.
                    finalColor = C.getOrDefault(se.getStatus(), se.getColor());
                    finalStatusLabel = se.getStatusLabel();
                    finalTip = se.getTip();
                    diagnosis = new Diagnosis(
                            se.getDiagnosisEstado(),
                            se.getDiagnosisConf(),
                            se.getDiagnosisSev(),
                            se.getDiagnosisConfidence()
                    );
                    actuators = new Actuadores(
                            // Derivado por tiempo del despacho (Regando / En cola / Cerrada): la columna
                            // sector.actuador_valve es legado y nadie la escribe ni la lee para decidir.
                            despacho.estadoValvula(se.getId()),
                            supplyDose != null ? supplyDose.estadoBomba(se.getId(), se.getActuadorPump()) : se.getActuadorPump(),
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
                        actuators,
                        se.getReentryUntil()
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
                    new LecturaZona(zoneMetrics, isStale ? null : VaporDeficit.of(ze.getDpvRaw()), ze.getLastReadingTime(), zoneAgo, isStale),
                    new NodoTestigo(
                            ze.getNodoMac(),
                            ze.getNodoBattery(),
                            ze.getNodoSignal(),
                            ze.getNodoBattery() != null && ze.getNodoBattery() < bateriaMinPct
                    ),
                    nutritionPlan != null ? nutritionPlan.status(ze) : null
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
                    DiagnosticoService.esConcluyente(d.estado()),
                    null, // derivada del sector: no tiene captura asociada
                    lecturaPorZona.get(s.zona()),
                    d.confidence(),
                    0 // derivada del sector: sin detecciones propias
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
                    null, // No concluyente no lleva score
                    EMDASH,
                    SEV_MAP.get(EMDASH).soft(),
                    SEV_MAP.get(EMDASH).ink(),
                    TINTS.get("No concluyente"),
                    agoPorZona.getOrDefault(s.zona(), "hace —"),
                    false,
                    null, // derivada del sector: no tiene captura asociada
                    lecturaPorZona.get(s.zona()),
                    null, // ni nivel de confianza
                    0
            ));
        }

        // Más recientes primero; sin fecha (zona que nunca reportó), al final
        diagnoses.sort(Comparator.comparing(DiagnosisCard::creadoEn,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // Diagnósticos persistidos (los que nacieron de una captura real) al frente de la
        // lista: son los más recientes y los únicos con fotografía. La vista no los distingue
        // de los derivados — así, cuando el modelo emita el diagnóstico en lugar del operario,
        // el dashboard no cambia en absoluto.
        diagnoses.addAll(0, cardsPersistidas());

        int totalSectores = allSectors.size();
        double pctSano = totalSectores > 0 ? Math.round((totalSano / (double) totalSectores) * 100) : 0;

        // KPIs de actividad autónoma: conteo real del historial del día de hoy.
        Map<String, Long> todayCounts = historialService.countToday();
        long actRiego   = todayCounts.getOrDefault("Riego", 0L);
        long actInsumo  = todayCounts.getOrDefault("Insumo", 0L);
        long actSombra  = todayCounts.getOrDefault("Mediasombra", 0L);
        long actToday   = actRiego + actInsumo + actSombra;

        Stats stats = new Stats(
                totalSectores, totalSano, totalWarning, totalCritical, totalOffline, totalAlerta,
                pctSano, (int) actToday, (int) actRiego, (int) actInsumo, (int) actSombra, diagnoses.size()
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

        // Feed de actividad del sistema: las últimas 8 acciones reales del historial.
        // Si el historial está vacío, la lista llega vacía — el frontend muestra
        // "Sin actividad registrada" en ese caso.
        List<ActionEvent> actions = historialService.getRecentActions(8);

        // Alertas de la campana: las de cada métrica de zona que pasó su punto de alerta (o está en
        // crítico) y las de los sectores críticos/warning. Crítico primero; hasta 8.
        List<Alert> alerts = new ArrayList<>(buildMetricAlerts(zonas, configuracionService.getAlertPoints()));
        alerts.addAll(buildAlertsFromHistorial(priority));
        alerts.sort(Comparator.comparingInt(a -> "CRITICAL".equals(a.level()) ? 0 : 1));
        alerts = alerts.stream().limit(MAX_ALERTAS).toList();

        // Clima: datos reales de Open-Meteo. En modo degradado (API caída) se
        // usa fallback con —, nunca valores hardcodeados.
        Weather weather = buildWeatherFromForecast(weatherService.getForecast());

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

    /**
     * Construye el DTO {@link Weather} a partir del pronóstico real de Open-Meteo.
     *
     * <p>Si la API climática está caída ({@code forecast} es {@code null}), todos los
     * campos muestran "N/A" o "—" — nunca valores hardcodeados que podrían confundir
     * al usuario creyendo que son datos reales.
     */
    private static Weather buildWeatherFromForecast(com.yerbanalytics.backend.engine.weather.WeatherForecast forecast) {
        if (forecast == null) {
            return new Weather(
                    0.0, "Sin datos", 0.0, 0.0, "N/A",
                    "Pronóstico no disponible (API climática degradada).",
                    List.of()
            );
        }

        double uv = forecast.uvIndex();
        String uvLabel = uv >= 8 ? "Muy Alto" : uv >= 6 ? "Alto" : uv >= 3 ? "Moderado" : "Bajo";

        // Texto de aviso de lluvia para el widget de riesgo
        double rainPct = forecast.probLluviaPct();
        String rainText;
        if (rainPct >= 60) {
            rainText = String.format("Lluvia probable (%.0f%%) — riego autónomo puede posponerse.", rainPct);
        } else if (rainPct >= 30) {
            rainText = String.format("Lluvia posible (%.0f%%). Monitoreo activo.", rainPct);
        } else {
            rainText = String.format("Sin lluvia inminente (%.0f%%). Operación normal.", rainPct);
        }

        List<ForecastSlot> slots = forecast.forecastSlots().stream()
                .map(s -> new ForecastSlot(s.label(), s.uv(), s.rain()))
                .toList();

        return new Weather(
                forecast.tempC(),
                forecast.cond(),
                forecast.humRel(),
                uv,
                uvLabel,
                rainText,
                slots
        );
    }

    /** Tope de alertas de la campana. */
    private static final int MAX_ALERTAS = 8;

    /**
     * Alertas por métrica de cada macro-zona (sólo las que afectan el estado y con lectura vigente):
     * {@code CRITICAL} en la franja roja; {@code WARNING} en la amarilla, pero sólo más allá de su punto
     * de alerta. Entre el punto y el ideal la zona sigue "En observación" sin avisar.
     */
    static List<Alert> buildMetricAlerts(List<Zona> zonas, Map<String, double[]> alertPoints) {
        List<Alert> out = new ArrayList<>();
        for (Zona z : zonas) {
            LecturaZona lectura = z.lectura();
            if (lectura == null || lectura.stale()) {
                continue;
            }
            for (Metric m : lectura.metrics()) {
                if (m.raw() == null || m.spec() == null || !Boolean.TRUE.equals(m.spec().afectaEstado())) {
                    continue;
                }
                double v = m.raw();
                String valor = m.value() + " " + m.unit();
                if ("critical".equals(m.status())) {
                    boolean bajo = v < m.spec().warn()[0];
                    double limite = bajo ? m.spec().warn()[0] : m.spec().warn()[1];
                    out.add(new Alert("CRITICAL", C.get("critical"), lectura.ago(), z.id(),
                            z.name() + " · " + m.label() + " " + valor + ": "
                                    + (bajo ? "debajo" : "arriba") + " del crítico (" + fmtLimite(limite) + " " + m.unit() + ")",
                            false));
                } else if ("warning".equals(m.status())) {
                    double[] punto = alertPoints.get(m.key());
                    if (punto == null) {
                        continue;
                    }
                    boolean bajo = v < punto[0];
                    if (bajo || v > punto[1]) {
                        double limite = bajo ? punto[0] : punto[1];
                        out.add(new Alert("WARNING", C.get("warning"), lectura.ago(), z.id(),
                                z.name() + " · " + m.label() + " " + valor + ": "
                                        + (bajo ? "debajo" : "arriba") + " del punto de alerta (" + fmtLimite(limite) + " " + m.unit() + ")",
                                false));
                    }
                }
            }
        }
        return out;
    }

    private static String fmtLimite(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /**
     * Construye la lista de alertas de la campana a partir de los sectores en estado crítico/warning.
     * Limita a 5 alertas, ordenadas por severidad.
     */
    private static List<Alert> buildAlertsFromHistorial(List<PriorityItem> priority) {
        return priority.stream()
                .limit(5)
                .map(p -> {
                    boolean isCritical = "Alta".equals(p.sev());
                    String level = isCritical ? "CRITICAL" : "WARNING";
                    String color = isCritical ? C.get("critical") : C.get("warning");
                    String msg = "Sector " + p.id() + ": " + p.reason();
                    return new Alert(level, color, "ahora", p.id(), msg, false);
                })
                .toList();
    }

    @Transactional
    public void updateTelemetry(String zoneId, MqttTelemetryPayload payload) {
        ZonaEntity zona = zonaRepository.findById(zoneId).orElse(null);
        if (zona == null) {
            return;
        }

        // El timestamp del contrato llega en segundos (firmware) o ms (simulador): se normaliza a
        // ms por valor. Si no sirve como fecha real, manda la hora de recepción.
        long recepcionMs = reloj.millis();
        Long tsMs = ContratoNodo.timestampAMs(payload.timestamp(), recepcionMs);
        if (tsMs == null) {
            if (zonasConTimestampInvalido.add(zoneId)) {
                log.warn("Zona {}: el nodo mandó un timestamp inutilizable ({}); se usa la hora de recepción. "
                        + "Sin NTP el nodo publica segundos desde el arranque.", zoneId, payload.timestamp());
            }
            tsMs = recepcionMs;
        } else {
            zonasConTimestampInvalido.remove(zoneId);
        }
        // Cualquier telemetría prueba que el nodo responde: corta la racha de pulsos sin respuesta.
        boolean rachaCortada = escalamiento.reiniciar(zona);
        // Lectura vieja (buffer offline del nodo, que conserva su hora original): no pisa una más
        // nueva ni cuenta como "recién leída" (tampoco responde al pulso). Sólo se ignora con timestamp legítimo.
        if (zona.getLastReadingTime() != null && tsMs < zona.getLastReadingTime()) {
            log.debug("Zona {}: lectura de {} ignorada por ser anterior a la última guardada ({})",
                    zoneId, tsMs, zona.getLastReadingTime());
            if (rachaCortada) {
                zonaRepository.save(zona);
            }
            return;
        }
        // S-03: sólo entra a la zona lo que está dentro del rango físico. Un valor fuera de rango pide la lectura
        // de nuevo y, si las relecturas también fallan, esa métrica se descarta en el ciclo (el resto sigue).
        // La unidad cambia una sola vez, acá: el contrato manda µS/cm de CE y la plataforma persiste dS/m.
        ReadingCheck check = readingGuard.check(zona, ReadingMetrics.from(payload.metrics()));

        // El motor decide sólo con la primera respuesta de la zona a un pulso con motor. Cualquier otra
        // telemetría (pulso sin motor, segunda respuesta, arranque del nodo, secuencia LECTURA) sólo actualiza
        // la lectura y el estado de los sectores. Una lectura que todavía espera su relectura no es la respuesta
        // definitiva: el pulso queda pendiente y el motor decide cuando llegue la que corrige (o la descartada).
        // La lectura de verificación de E-01 (que pidió el servicio de efectividad tras un riego) tampoco es del pulso: se guarda
        // y nada más, así el pico de humedad del riego no dispara reglas ni cuenta como respuesta.
        PulsoSensadoService.PulseResponse pulseResponse = check.awaitsReread() || isVerificationReading(zoneId, recepcionMs)
                ? PulsoSensadoService.PulseResponse.NONE
                : pulsos.recordResponse(zoneId, recepcionMs);
        boolean ejecutaMotor = pulseResponse.runsEngine();
        List<SectorEntity> sectors = sectorRepository.findByZonaId(zoneId);

        // La lectura se persiste UNA vez, en la zona. Sólo se actualizan las métricas aceptadas: una lectura
        // parcial (un sensor en falla, un valor descartado o un envío manual de una sola métrica) conserva el
        // último valor de las demás.
        check.accepted().asMap().forEach(zona::setRaw);
        if (check.accepted().has("humSus")) {
            // Marca propia: si la sonda cae, el resto de la lectura sigue refrescando la zona
            // y sólo esta marca delata que la humedad está congelada (StaleSensorRule).
            zona.setHumSusTs(tsMs);
        }
        // El DPV deriva de T y HR de ESTA lectura; si falta alguna (o se descartó) conserva el valor y la marca viejos.
        Double dpv = VaporPressureDeficit.calculate(check.accepted().get("temp"), check.accepted().get("humAmb"));
        if (dpv != null) {
            zona.setDpvRaw(VaporPressureDeficit.round(dpv));
            zona.setDpvTs(tsMs);
        }
        if (payload.mac() != null && !payload.mac().isBlank()) zona.setNodoMac(payload.mac().trim());
        if (payload.battery() != null) zona.setNodoBattery(payload.battery());
        if (payload.signal() != null) zona.setNodoSignal(payload.signal());
        zona.setLastReadingTime(tsMs);
        // Misma transacción que la lectura: si falla el historial, la zona tampoco se actualiza.
        readings.record(zona, tsMs, recepcionMs, pulseResponse.responded() ? ReadingOrigin.PULSE : ReadingOrigin.OTHER);
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

        // Lo que las reglas de riego necesitan del pasado reciente, UNA consulta por zona (no una por sector y
        // por regla): último riego, último riego crítico y última aplicación de cada sector.
        Instant ahora = reloj.instant();
        Map<String, ContextoRiego> riegoPorSector = Map.of();
        Map<String, HardwareCoverage> hardwarePorSector = Map.of();
        Map<String, HardwareFaults> fallasPorSector = Map.of();
        WeatherForecast forecast = null;
        if (ejecutaMotor) {
            // S-04: qué actuadores tiene mapeado cada sector, también UNA vez por zona (dos consultas, no una por regla).
            List<String> sectorIds = sectors.stream().map(SectorEntity::getId).toList();
            hardwarePorSector = hardwareService.coverageOf(zoneId, sectorIds);
            // S-05: cuáles de esos actuadores están averiados, también UNA vez por zona.
            fallasPorSector = hardwareService.faultsOf(sectorIds);
            Instant inicioCiclo = CicloLectura.inicio(ahora, intervaloSensadoMinutos());
            riegoPorSector = contextosDeRiego(zoneId, zona, sectors, inicioCiclo, ahora);
            // El pronóstico, UNA vez por evaluación de zona (no por sector) y sin esperar: este hilo es el del broker
            // MQTT y sostiene a R-02. Sin pronóstico cacheado se evalúa sin él (la traza lo muestra como sin dato).
            forecast = weatherService.getForecastSinEspera();
        }

        for (SectorEntity s : sectors) {
            s.setStatus(finalStatus);
            s.setColor(C.get(finalStatus));
            s.setStatusLabel(LAB.get(finalStatus));
            s.setTip(s.getId() + " · " + LAB.get(finalStatus));

            // El diagnóstico no se toca: sólo lo cambia el alta de diagnósticos (DiagnosticoService).

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

            if (!ejecutaMotor) {
                continue;
            }
            // Motor de Reglas: delega la decisión de actuación al orquestador.
            // El ActionExecutor materializa las acciones (actualiza actuadores y persiste historial).
            RuleContext ctx = buildRuleContext(s, tempMetrics, finalStatus, ahora, forecast, riegoPorSector.get(s.getId()),
                    check.invalid(), hardwarePorSector.getOrDefault(s.getId(), HardwareCoverage.NONE),
                    fallasPorSector.getOrDefault(s.getId(), HardwareFaults.NONE));
            // La traza queda en memoria (última por sector y origen); no toca la base.
            ResultadoEvaluacion resultado = ruleOrchestrator.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
            trazaStore.guardar(resultado.traza());
            actionExecutor.execute(resultado.acciones(), ctx, OrigenEvaluacion.TELEMETRIA);
            // Una regla de diagnóstico se evaluó: el diagnóstico queda usado, en esta misma transacción (si revierte,
            // sigue siendo nuevo para el pulso siguiente).
            if (resultado.usoDiagnostico()) {
                s.setDiagnosisUsadoTs(s.getDiagnosisTs());
            }
        }
        sectorRepository.saveAll(sectors);

        // Las reglas de la macro-zona (plan de nutrición) corren UNA vez, con los sectores ya evaluados y la misma lectura.
        if (ejecutaMotor && zoneRules != null) {
            zoneRules.evaluate(zona, sectors, ahora, hardwarePorSector, fallasPorSector);
        }

        // Heartbeat del nodo testigo de la zona: batería/señal/último update (HU-21 CA-01).
        // Antes el mac/battery del payload se descartaban; ahora alimentan el registro de hardware.
        hardwareService.actualizarHeartbeat(zoneId, payload.mac(), payload.battery(), payload.signal(), tsMs);
    }

    /**
     * Construye el {@link RuleContext} para un sector en el ciclo de evaluación actual.
     *
     * <p>Puebla todos los campos del snapshot:
     * <ul>
         *   <li>{@code forecast} — el de la zona, pedido UNA vez a {@link WeatherService} (null en modo degradado).</li>
     *   <li>{@code bloqueoManualActivo} — consulta {@link ManualLockRepository} por sector y zona.</li>
     *   <li>{@code invalidMetrics} — las métricas descartadas en esta lectura por estar fuera de rango (S-03).</li>
     *   <li>{@code hardware} — los actuadores mapeados del sector y su zona; un sector que no figura no tiene ninguno (S-04).</li>
     *   <li>{@code faults} — los actuadores del sector y su zona marcados como averiados; un sector que no figura no tiene ninguno (S-05).</li>
     * </ul>
     */
    private RuleContext buildRuleContext(SectorEntity s, List<Metric> metrics, String finalStatus,
                                         Instant ahora, WeatherForecast forecast, ContextoRiego riego,
                                         Set<String> invalidMetrics, HardwareCoverage hardware,
                                         HardwareFaults faults) {
        ZonaEntity zona = s.getZona();

        boolean bloqueoActivo = !manualLockRepository.findBySectorIdAndActiveTrue(s.getId()).isEmpty()
                || (zona != null && !manualLockRepository.findByZonaIdAndActiveTrue(zona.getId()).isEmpty());

        return new RuleContext(
                s,
                zona,
                configuracionService.getEffectiveSpecs(),
                metrics,
                configuracionService.getConfiguracionOperativa(),
                finalStatus,
                ahora,
                forecast,
                bloqueoActivo,
                riego != null ? riego : ContextoRiego.vacio(),
                RuleOverrides.NONE,
                invalidMetrics,
                hardware,
                faults
        );
    }

    /** Intervalo de sensado guardado (el ciclo de lectura lo acota a 60–360 min); 240 si no hay configuración. */
    private int intervaloSensadoMinutos() {
        ConfiguracionOperativaEntity op = configuracionService.getConfiguracionOperativa();
        return op != null && op.getIntervaloSensadoMinutos() != null ? op.getIntervaloSensadoMinutos() : 240;
    }

    /**
     * Arma el {@link ContextoRiego} de cada sector de la zona con UNA consulta agrupada. El último riego de
     * un sector es el mayor {@code ts} de sus filas "Riego"; el último ordenado por R-02, el de la fila cuya
     * regla es {@code DeficitCriticoRule}; la última aplicación, la de sus filas "Insumo". El riego en curso
     * lo da el despacho (memoria), que además cubre la ventana antes de que el riego llegue al historial y, si el
     * registro falla, recuerda el último riego (y el último crítico) para que el ciclo y el tope de R-02 no lo olviden.
     */
    private Map<String, ContextoRiego> contextosDeRiego(String zonaId, ZonaEntity zona, List<SectorEntity> sectores,
                                                        Instant inicioCiclo, Instant ahora) {
        long desde = ahora.toEpochMilli() - VENTANA_HISTORIAL_RIEGO_MS;
        Map<String, Long> riego = new HashMap<>();
        Map<String, Long> riegoCritico = new HashMap<>();
        Map<String, Long> aplicacion = new HashMap<>();
        for (HistorialRepository.UltimoEvento f : historialRepository.ultimosPorSector(zonaId, desde)) {
            if ("Riego".equals(f.getTipo())) {
                riego.merge(f.getSectorId(), f.getTs(), Math::max);
                if (DeficitCriticoRule.NAME.equals(f.getRegla())) {
                    riegoCritico.merge(f.getSectorId(), f.getTs(), Math::max);
                }
            } else if ("Insumo".equals(f.getTipo())) {
                aplicacion.merge(f.getSectorId(), f.getTs(), Math::max);
            }
        }
        Map<String, ContextoRiego> out = new HashMap<>();
        for (SectorEntity s : sectores) {
            // Lo que dice el historial y lo que recuerda el despacho en memoria (el riego que se abrió pero cuyo
            // registro falló): el ciclo y el tope de R-02 miran el más reciente de los dos.
            out.put(s.getId(), new ContextoRiego(inicioCiclo,
                    masReciente(riego.get(s.getId()), despacho.ultimoRiegoMs(s.getId())),
                    masReciente(riegoCritico.get(s.getId()), despacho.ultimoRiegoCriticoMs(s.getId())),
                    aplicacion.get(s.getId()), despacho.finRiegoEnCurso(s.getId()), zona.getHumSusTs()));
        }
        return out;
    }

    private static Long masReciente(Long a, Long b) {
        return a == null ? b : (b == null ? a : Long.valueOf(Math.max(a, b)));
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

        List<DiagnosticoEntity> persistidos = diagnosticoService.entidadesRecientes();
        Map<String, String> imagenes = diagnosticoService.imagenUrls(persistidos);
        List<DiagnosisCard> out = new ArrayList<>();
        for (var e : persistidos) {
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
                    DiagnosticoService.esConcluyente(e.getEstado()),
                    imagenes.get(e.getCapturaId()),
                    e.getCreadoEn(),
                    e.getConfidence(),
                    e.getDetectionCount()
            ));
        }
        return out;
    }

    private String formatAgo(long timestamp) {
        long diffMs = reloj.millis() - timestamp;
        if (diffMs < 0) diffMs = 0;
        long diffSec = diffMs / 1000;
        if (diffSec < 60) {
            return "hace " + diffSec + " s";
        }
        long diffMin = diffSec / 60;
        return "hace " + diffMin + " min";
    }
}
