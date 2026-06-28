package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.ColorPair;
import com.yerbanalytics.backend.dto.Evolution;
import com.yerbanalytics.backend.dto.HistorialEvento;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Registro, consulta y evaluación de efectividad del historial de acciones.
 * El historial es inmutable: este servicio sólo inserta y completa el seguimiento,
 * nunca borra ni permite editar la cadena de justificación de un evento.
 */
@Service
public class HistorialService {

    private static final ColorPair VERDICT_EFECTIVA = new ColorPair("#E7F1EA", "#2E7A4F");
    private static final ColorPair VERDICT_SEGUIMIENTO = new ColorPair("#F3ECDD", "#8A6A22");
    private static final ColorPair VERDICT_SIN = new ColorPair("#FBE6E0", "#A8331C");

    private static final DateTimeFormatter FECHA_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final HistorialRepository historialRepository;
    private final SectorRepository sectorRepository;
    private final ConfiguracionService configuracionService;
    /** Fallbacks por properties, usados si la configuración persistida no está disponible. */
    private final long latencyMs;
    private final String latencyLabel;
    private final double umbralRecuperacion;

    public HistorialService(HistorialRepository historialRepository,
                            SectorRepository sectorRepository,
                            @Lazy ConfiguracionService configuracionService,
                            @Value("${yerbanalytics.historial.latency-ms:120000}") long latencyMs,
                            @Value("${yerbanalytics.historial.latency-label:2 min}") String latencyLabel,
                            @Value("${yerbanalytics.historial.umbral-recuperacion:5}") double umbralRecuperacion) {
        this.historialRepository = historialRepository;
        this.sectorRepository = sectorRepository;
        this.configuracionService = configuracionService;
        this.latencyMs = latencyMs;
        this.latencyLabel = latencyLabel;
        this.umbralRecuperacion = umbralRecuperacion;
    }

    // ------------------------------------------------------------------
    // Registro (invocado desde la lógica de actuación de NurseryService)
    // ------------------------------------------------------------------

    /** Registra un riego autónomo por sector, con seguimiento de humedad de sustrato. */
    public void registrarRiego(SectorEntity s) {
        double hum = s.getHumSusRaw() != null ? s.getHumSusRaw() : 0.0;
        HistorialEventoEntity e = base(s, "Riego", "Efectiva");
        e.setLectura("Humedad de sustrato " + fmt0(hum) + "% bajo el umbral mínimo configurado.");
        e.setDecision("El motor de reglas ordena abrir la electroválvula del sector.");
        e.setAccion("Microaspersor abierto · riego autónomo en curso.");
        withSeguimiento(e, hum);
        historialRepository.save(e);
    }

    /** Registra una dosificación de insumo, con seguimiento de la métrica afectada. */
    public void registrarInsumo(SectorEntity s) {
        double hum = s.getHumSusRaw() != null ? s.getHumSusRaw() : 0.0;
        Double conf = s.getDiagnosisConf();
        HistorialEventoEntity e = base(s, "Insumo", "En seguimiento");
        e.setLectura("Diagnóstico IA: " + s.getDiagnosisEstado()
                + (conf != null ? " (confianza " + fmt0(conf) + "%)" : "") + ".");
        e.setDecision("Confianza sobre el umbral: el motor habilita la dosificación localizada.");
        e.setAccion("Bomba peristáltica inyectando insumo en la línea del sector.");
        withSeguimiento(e, hum);
        historialRepository.save(e);
    }

    private HistorialEventoEntity base(SectorEntity s, String tipo, String res) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(s.getId());
        e.setZonaId(s.getZona().getId());
        e.setZonaName(s.getZona().getName());
        e.setTipo(tipo);
        e.setTs(System.currentTimeMillis());
        e.setRes(res);
        e.setSev(s.getDiagnosisSev());
        e.setBloqueoRepeticion(false);
        return e;
    }

    private void withSeguimiento(HistorialEventoEntity e, double valorAntes) {
        // Latencia y delta vienen de la configuración agronómica vigente (HU-15 CA-07),
        // con los valores de properties como fallback.
        long effLatency = latencyMs;
        String effLabel = latencyLabel;
        double effUmbral = umbralRecuperacion;
        try {
            effLatency = configuracionService.getLatencyMs();
            effLabel = configuracionService.getLatencyLabel();
            effUmbral = configuracionService.getUmbralRecuperacion();
        } catch (RuntimeException ignored) {
            // Sin configuración disponible: se usan los fallbacks por properties.
        }

        e.setMetricKey("humSus");
        e.setValorAntes(valorAntes);
        e.setUmbralRecuperacion(effUmbral);
        e.setLatencyMs(effLatency);
        e.setEvoShow(true);
        e.setEvoMetric("Humedad de sustrato");
        e.setEvoUnit("%");
        e.setEvoAntes(fmt0(valorAntes));
        e.setEvoAhora("—");
        e.setEvoDelta("—");
        e.setEvoLatencia(effLabel);
        e.setEvoVerdict("En seguimiento");
        e.setEvoEvaluadoTs(null);
    }

    /**
     * Registra, de forma inmutable, un cambio de configuración agronómica (HU-15 CA-02):
     * deja asentado quién y cuándo recalibró el sistema.
     */
    @Transactional
    public void registrarConfiguracion(String usuario) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId("—");
        e.setZonaId("—");
        e.setZonaName("Sistema");
        e.setTipo("Configuración");
        e.setTs(System.currentTimeMillis());
        e.setLectura("Recalibración de parámetros agronómicos por " + usuario + ".");
        e.setDecision("Se validaron los nuevos umbrales y límites contra el rango fisiológico.");
        e.setAccion("Configuración actualizada: umbrales, límites operativos y plan de rustificación.");
        e.setRes("Efectiva");
        e.setSev("—");
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    // ------------------------------------------------------------------
    // Seguimiento post-acción (HU-12) — corre periódicamente
    // ------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${yerbanalytics.historial.eval-interval-ms:30000}")
    @Transactional
    public void evaluarSeguimiento() {
        long now = System.currentTimeMillis();
        List<HistorialEventoEntity> pendientes = historialRepository.findByEvoShowTrueAndEvoEvaluadoTsIsNull();
        List<HistorialEventoEntity> evaluados = new ArrayList<>();

        for (HistorialEventoEntity e : pendientes) {
            if (e.getLatencyMs() == null || now - e.getTs() < e.getLatencyMs()) {
                continue; // la latencia todavía no venció
            }
            SectorEntity s = sectorRepository.findById(e.getSectorId()).orElse(null);
            Double ahora = s != null ? currentMetric(s, e.getMetricKey()) : null;
            if (ahora == null) {
                continue; // sin lectura disponible, se reintenta en el próximo ciclo
            }

            double antes = e.getValorAntes() != null ? e.getValorAntes() : 0.0;
            double delta = ahora - antes;
            double umbral = e.getUmbralRecuperacion() != null ? e.getUmbralRecuperacion() : umbralRecuperacion;
            boolean efectiva = Math.abs(delta) >= umbral;

            e.setEvoAhora(fmt0(ahora));
            e.setEvoDelta((delta >= 0 ? "+" : "") + fmt0(delta));
            e.setEvoVerdict(efectiva ? "Efectiva" : "Sin efectividad");
            e.setBloqueoRepeticion(!efectiva);
            e.setEvoEvaluadoTs(now);
            evaluados.add(e);
        }

        if (!evaluados.isEmpty()) {
            historialRepository.saveAll(evaluados);
        }
    }

    private Double currentMetric(SectorEntity s, String key) {
        if (key == null) return null;
        return switch (key) {
            case "humSus" -> s.getHumSusRaw();
            case "humAmb" -> s.getHumAmbRaw();
            case "temp" -> s.getTempRaw();
            case "ce" -> s.getCeRaw();
            case "uv" -> s.getUvRaw();
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Consulta (endpoint)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<HistorialEvento> getHistorial(String sector, String zona, String tipo, Long desde, Long hasta) {
        return historialRepository.findFiltered(blank(sector), blank(zona), blank(tipo), desde, hasta)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private HistorialEvento toDto(HistorialEventoEntity e) {
        ColorPair rm = RES_MAP.getOrDefault(e.getRes(), new ColorPair("#EEEDE5", "#6A776E"));
        ActMeta meta = ACT.getOrDefault(e.getTipo(), ACT.get("Riego"));

        Evolution evo = null;
        if (e.isEvoShow()) {
            ColorPair vc = verdictColor(e.getEvoVerdict());
            evo = new Evolution(
                    true,
                    e.getEvoMetric(),
                    e.getEvoAntes(),
                    e.getEvoAhora(),
                    e.getEvoUnit(),
                    e.getEvoDelta(),
                    e.getEvoLatencia(),
                    e.getEvoVerdict(),
                    vc.soft(),
                    vc.ink()
            );
        }

        return new HistorialEvento(
                e.getId(),
                e.getSectorId(),
                e.getZonaName(),
                e.getTipo(),
                formatAgo(e.getTs()),
                e.getTs(),
                FECHA_FMT.format(Instant.ofEpochMilli(e.getTs())),
                e.getLectura(),
                e.getDecision(),
                e.getAccion(),
                e.getRes(),
                rm.soft(),
                rm.ink(),
                e.getSev(),
                meta.tint(),
                meta.ink(),
                meta.path(),
                evo
        );
    }

    private ColorPair verdictColor(String verdict) {
        if ("Efectiva".equals(verdict)) return VERDICT_EFECTIVA;
        if ("Sin efectividad".equals(verdict)) return VERDICT_SIN;
        return VERDICT_SEGUIMIENTO;
    }

    private static String blank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String fmt0(double v) {
        return String.format(Locale.US, "%.0f", v);
    }

    private static String formatAgo(long timestamp) {
        long diffMs = Math.max(0, System.currentTimeMillis() - timestamp);
        long diffSec = diffMs / 1000;
        if (diffSec < 60) return "hace " + diffSec + " s";
        long diffMin = diffSec / 60;
        if (diffMin < 60) return "hace " + diffMin + " min";
        long diffH = diffMin / 60;
        if (diffH < 24) return "hace " + diffH + " h";
        return "hace " + (diffH / 24) + " d";
    }
}
