package com.yerbanalytics.backend.service.mock;

import com.yerbanalytics.backend.dto.ActionEvent;
import com.yerbanalytics.backend.dto.Actuadores;
import com.yerbanalytics.backend.dto.Alert;
import com.yerbanalytics.backend.dto.Diagnosis;
import com.yerbanalytics.backend.dto.DiagnosisCard;
import com.yerbanalytics.backend.dto.ForecastSlot;
import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.dto.PriorityItem;
import com.yerbanalytics.backend.dto.Sector;
import com.yerbanalytics.backend.dto.Stats;
import com.yerbanalytics.backend.dto.Weather;
import com.yerbanalytics.backend.dto.Zona;
import com.yerbanalytics.backend.service.mock.NurseryConstants.ActTemplate;
import com.yerbanalytics.backend.service.mock.NurseryConstants.PathoEntry;
import com.yerbanalytics.backend.service.mock.NurseryConstants.ZonaDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.service.mock.NurseryConstants.ACT;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.ACT_TPL;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.C;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.EMDASH;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.LAB;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.PATHOS;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.RES_MAP;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.SEV_MAP;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.SPECS;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.TINTS;
import static com.yerbanalytics.backend.service.mock.NurseryConstants.ZONA_DEFS;

/**
 * Generador del vivero — port de {@code frontend/src/data/mock/generators.ts}.
 * El orden de llamadas al RNG define la salida; no reordenar.
 */
public class NurseryGenerator {

    public NurseryData build(int seed) {
        Mulberry32Rng r = new Mulberry32Rng(seed);

        List<Sector> sectors = new ArrayList<>();
        Map<String, Sector> byId = new LinkedHashMap<>();
        List<Zona> zonas = new ArrayList<>();

        for (ZonaDef z : ZONA_DEFS) {
            List<Sector> list = new ArrayList<>();
            int sano = 0;
            int alerta = 0;
            int off = 0;
            for (int i = 1; i <= 100; i++) {
                double u = r.next();
                String status = "ok";
                if (u > 0.978) {
                    status = "offline";
                } else if (u > 0.94) {
                    status = "critical";
                } else if (u > 0.83) {
                    status = "warning";
                }
                Sector s = makeSector(z, i, status, r);
                list.add(s);
                sectors.add(s);
                byId.put(s.id(), s);
                if ("ok".equals(status)) {
                    sano++;
                } else if ("offline".equals(status)) {
                    off++;
                } else {
                    alerta++;
                }
            }
            zonas.add(new Zona(z.id(), z.name(), z.sub(), list, sano, alerta, off, 100));
        }

        int sano = countStatus(sectors, "ok");
        int warning = countStatus(sectors, "warning");
        int critical = countStatus(sectors, "critical");
        int offline = countStatus(sectors, "offline");
        Stats stats = new Stats(
                600, sano, warning, critical, offline, warning + critical,
                (double) Math.round((sano / 600.0) * 100), 41, 28, 7, 6, 0
        );

        Map<String, Integer> order = Map.of("critical", 0, "warning", 1);
        List<PriorityItem> priority = sectors.stream()
                .filter(s -> "critical".equals(s.status()) || "warning".equals(s.status()))
                .sorted(Comparator
                        .comparingInt((Sector s) -> order.get(s.status()))
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

        int[] diagCounter = {0};
        List<DiagnosisCard> diagnoses = new ArrayList<>(sectors.stream()
                .filter(s -> !"Sano".equals(s.diagnosis().estado()) && !"Sin diagn\u00f3stico".equals(s.diagnosis().estado()))
                .map(s -> {
                    diagCounter[0]++;
                    Diagnosis d = s.diagnosis();
                    return new DiagnosisCard(
                            "DG-" + String.format("%03d", diagCounter[0]),
                            s.id(),
                            s.zonaName(),
                            d.estado(),
                            d.conf(),
                            d.sev(),
                            SEV_MAP.get(d.sev()).soft(),
                            SEV_MAP.get(d.sev()).ink(),
                            TINTS.getOrDefault(d.estado(), TINTS.get("Sin diagn\u00f3stico")),
                            s.ago(),
                            d.conf() >= 85
                    );
                })
                .toList());

        List<Sector> ncSectors = sectors.stream()
                .filter(s -> "warning".equals(s.status()))
                .limit(3)
                .toList();
        for (Sector s : ncSectors) {
            diagCounter[0]++;
            diagnoses.add(new DiagnosisCard(
                    "DG-" + String.format("%03d", diagCounter[0]),
                    s.id(),
                    s.zonaName(),
                    "No concluyente",
                    (double) Math.round(rr(r, 62, 83)),
                    EMDASH,
                    SEV_MAP.get(EMDASH).soft(),
                    SEV_MAP.get(EMDASH).ink(),
                    TINTS.get("No concluyente"),
                    s.ago(),
                    false
            ));
        }
        diagnoses.sort(Comparator.comparing(DiagnosisCard::time));
        stats = new Stats(
                stats.total(), stats.sano(), stats.warning(), stats.critical(), stats.offline(),
                stats.alerta(), stats.sanoPct(), stats.actToday(), stats.actRiego(),
                stats.actInsumo(), stats.actSombra(), diagnoses.size()
        );

        Map<String, DiagnosisCard> diagById = new LinkedHashMap<>();
        for (DiagnosisCard d : diagnoses) {
            diagById.put(d.id(), d);
        }
        List<DiagnosisCard> recentDiag = diagnoses.stream()
                .filter(d -> !"No concluyente".equals(d.estado()))
                .limit(5)
                .toList();

        List<Sector> actSrc = sectors.stream()
                .filter(s -> !"ok".equals(s.status()) && !"offline".equals(s.status()))
                .toList();
        List<ActionEvent> actions = new ArrayList<>();
        for (int i = 0; i < ACT_TPL.size(); i++) {
            ActTemplate a = ACT_TPL.get(i);
            Sector sec = actSrc.isEmpty() ? null : actSrc.get(i % actSrc.size());
            var meta = ACT.get(a.tipo());
            var rm = RES_MAP.get(a.res());
            actions.add(new ActionEvent(
                    a.title() + " \u00b7 " + (sec != null ? sec.id() : "MZ-2-014"),
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

        List<Alert> alerts = List.of(
                new Alert("CRITICAL", C.get("critical"), "14:08",
                        priority.isEmpty() ? "MZ-3-077" : priority.get(0).id(),
                        "Da\u00f1o f\u00fangico confirmado + humedad de sustrato 84%. Dosificaci\u00f3n de fungicida en curso.", false),
                new Alert("CRITICAL", C.get("critical"), "13:41", "MZ-5-042",
                        "Falla hidr\u00e1ulica: caudal\u00edmetro sin flujo tras abrir electrov\u00e1lvula. Sector marcado para revisi\u00f3n.", false),
                new Alert("WARNING", C.get("warning"), "13:20", "MZ-2-091",
                        "Nodo testigo con bater\u00eda baja (18%). Recambio preventivo sugerido.", false),
                new Alert("WARNING", C.get("warning"), "12:55",
                        priority.size() > 2 ? priority.get(2).id() : "MZ-1-033",
                        "Clorosis detectada (confianza 88%). A la espera de validaci\u00f3n de dosis nutricional.", false),
                new Alert("WARNING", C.get("warning"), "11:30", "MZ-4-005",
                        "Sensor sin reporte hace 2 h \u2014 se\u00f1al intermitente. Mostrando \u00faltimo dato conocido.", false)
        );

        Weather weather = new Weather(
                21.0,
                "Parcial nublado",
                78.0,
                7.0,
                "Alto",
                "Lluvia probable en ~3 h \u2014 riego aut\u00f3nomo pospuesto en 2 macro-zonas.",
                List.of(
                        new ForecastSlot("15 h", 7.0, 10.0),
                        new ForecastSlot("18 h", 3.0, 60.0),
                        new ForecastSlot("21 h", 0.0, 80.0),
                        new ForecastSlot("Ma\u00f1ana", 6.0, 25.0)
                )
        );

        return new NurseryData(
                zonas,
                sectors,
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
                SPECS
        );
    }

    private Sector makeSector(ZonaDef z, int i, String status, Mulberry32Rng r) {
        String id = z.id() + "-" + String.format("%03d", i);
        int offIdx = "ok".equals(status) || "offline".equals(status) ? -1 : (int) Math.floor(r.next() * SPECS.size());

        List<Metric> metrics = new ArrayList<>();
        for (int mi = 0; mi < SPECS.size(); mi++) {
            MetricSpec sp = SPECS.get(mi);
            double v;
            if (mi == offIdx && "warning".equals(status)) {
                if (r.next() > 0.5) {
                    v = rr(r, sp.warn()[1] - (sp.warn()[1] - sp.ideal()[1]) * 0.5, sp.warn()[1]);
                } else {
                    v = rr(r, sp.warn()[0], sp.ideal()[0]);
                }
            } else if (mi == offIdx && "critical".equals(status)) {
                if (r.next() > 0.5) {
                    v = rr(r, sp.warn()[1], sp.crit()[1]);
                } else {
                    v = rr(r, sp.crit()[0], sp.warn()[0]);
                }
            } else {
                v = rr(r,
                        sp.ideal()[0] + (sp.ideal()[1] - sp.ideal()[0]) * 0.12,
                        sp.ideal()[1] - (sp.ideal()[1] - sp.ideal()[0]) * 0.12);
            }
            String ms = metricStatus(v, sp);
            metrics.add(new Metric(
                    sp.key(),
                    sp.label(),
                    sp.unit(),
                    v,
                    formatValue(v, sp.dec()),
                    ms,
                    C.get(ms),
                    sp
            ));
        }

        String realStatus;
        if ("offline".equals(status)) {
            realStatus = "offline";
        } else if (metrics.stream().anyMatch(m -> "critical".equals(m.status()))) {
            realStatus = "critical";
        } else if (metrics.stream().anyMatch(m -> "warning".equals(m.status()))) {
            realStatus = "warning";
        } else {
            realStatus = "ok";
        }
        String finalStatus = "offline".equals(status) ? "offline" : realStatus;

        Diagnosis diagnosis;
        if ("offline".equals(finalStatus)) {
            diagnosis = new Diagnosis("Sin diagn\u00f3stico", null, EMDASH);
        } else if ("ok".equals(finalStatus)) {
            diagnosis = new Diagnosis("Sano", (double) Math.round(rr(r, 95, 99)), EMDASH);
        } else if (r.next() > 0.86) {
            diagnosis = new Diagnosis("No concluyente", (double) Math.round(rr(r, 64, 83)), EMDASH);
        } else {
            PathoEntry p = pick(r, PATHOS.get(finalStatus));
            diagnosis = new Diagnosis(p.estado(), (double) Math.round(rr(r, 86, 98)), p.sev());
        }

        String reason;
        if ("offline".equals(finalStatus)) {
            reason = "Sin reporte de telemetr\u00eda \u00b7 se\u00f1al perdida";
        } else if ("ok".equals(finalStatus)) {
            reason = "Todos los par\u00e1metros en rango \u00f3ptimo";
        } else {
            Metric bad = metrics.stream()
                    .filter(m -> finalStatus.equals(m.status()))
                    .findFirst()
                    .orElseGet(() -> metrics.stream().filter(m -> !"ok".equals(m.status())).findFirst().orElse(null));
            if (bad != null) {
                reason = bad.label() + " " + bad.value() + ("%".equals(bad.unit()) ? "%" : " " + bad.unit());
            } else {
                reason = diagnosis.estado();
            }
        }

        Metric humSus = metrics.get(0);
        String valve = !"offline".equals(finalStatus) && humSus.raw() < 42 ? "Regando" : "Cerrada";
        String pump = "critical".equals(finalStatus) && diagnosis.conf() != null && diagnosis.conf() >= 85
                ? "Dosificando" : "En espera";
        int shadePct = (int) (Math.round(rr(r, 30, 65) / 5) * 5);

        int mins = (int) Math.round(rr(r, 4, 28));
        String ago = "offline".equals(finalStatus)
                ? "hace " + Math.round(rr(r, 24, 31)) + " h"
                : "hace " + mins + " min";

        return new Sector(
                id,
                z.id(),
                z.name(),
                i,
                finalStatus,
                C.get(finalStatus),
                LAB.get(finalStatus),
                id + " \u00b7 " + LAB.get(finalStatus),
                metrics,
                diagnosis,
                reason,
                new Actuadores(valve, pump, shadePct),
                ago,
                "offline".equals(finalStatus)
        );
    }

    private static String metricStatus(double v, MetricSpec sp) {
        if (v < sp.warn()[0] || v > sp.warn()[1]) {
            return "critical";
        }
        if (v < sp.ideal()[0] || v > sp.ideal()[1]) {
            return "warning";
        }
        return "ok";
    }

    private static String formatValue(double v, int dec) {
        return String.format("%." + dec + "f", v);
    }

    private static double rr(Mulberry32Rng r, double a, double b) {
        return a + (b - a) * r.next();
    }

    private static <T> T pick(Mulberry32Rng r, List<T> arr) {
        return arr.get((int) Math.floor(r.next() * arr.size()));
    }

    private static int countStatus(List<Sector> sectors, String status) {
        return (int) sectors.stream().filter(s -> status.equals(s.status())).count();
    }
}
