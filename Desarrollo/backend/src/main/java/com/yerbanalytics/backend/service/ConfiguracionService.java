package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.dto.RustificacionEtapa;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.model.UmbralMetricaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.SPECS;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración agronómica (HU-15): lectura, validación y persistencia de umbrales,
 * límites operativos y plan de rustificación. Es la fuente de verdad en runtime para
 * los umbrales de métricas ({@link #getEffectiveSpecs()}) y los parámetros de
 * seguimiento post-acción que consumen {@code NurseryService} e {@code HistorialService}.
 */
@Service
public class ConfiguracionService {

    /** Identidad de la fila única de configuración operativa. */
    private static final int OPERATIVA_ID = 1;

    private final UmbralMetricaRepository umbralRepository;
    private final ConfiguracionOperativaRepository operativaRepository;
    private final RustificacionEtapaRepository rustificacionRepository;
    private final HistorialService historialService;

    /** Metadatos de fábrica por clave de métrica (label, unit, dec, base, envelope). */
    private final Map<String, MetricSpec> factory;

    /** Cache de las specs efectivas (5 filas, camino caliente de telemetría). */
    private volatile List<MetricSpec> effectiveSpecsCache;
    /** Cache de la configuración operativa. */
    private volatile ConfiguracionOperativaEntity operativaCache;

    public ConfiguracionService(UmbralMetricaRepository umbralRepository,
                                ConfiguracionOperativaRepository operativaRepository,
                                RustificacionEtapaRepository rustificacionRepository,
                                @Lazy HistorialService historialService) {
        this.umbralRepository = umbralRepository;
        this.operativaRepository = operativaRepository;
        this.rustificacionRepository = rustificacionRepository;
        this.historialService = historialService;

        Map<String, MetricSpec> map = new LinkedHashMap<>();
        for (MetricSpec sp : SPECS) {
            map.put(sp.key(), sp);
        }
        this.factory = map;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Configuracion getConfiguracion() {
        return new Configuracion(buildUmbralesDto(), buildOperativaDto(loadOperativa()), buildRustificacionDto());
    }

    private List<UmbralMetrica> buildUmbralesDto() {
        Map<String, UmbralMetricaEntity> persisted = new LinkedHashMap<>();
        for (UmbralMetricaEntity e : umbralRepository.findAll()) {
            persisted.put(e.getMetricKey(), e);
        }
        List<UmbralMetrica> out = new ArrayList<>();
        for (MetricSpec sp : SPECS) {
            UmbralMetricaEntity e = persisted.get(sp.key());
            double idealMin = e != null ? e.getIdealMin() : sp.ideal()[0];
            double idealMax = e != null ? e.getIdealMax() : sp.ideal()[1];
            double warnMin = e != null ? e.getWarnMin() : sp.warn()[0];
            double warnMax = e != null ? e.getWarnMax() : sp.warn()[1];
            double critMin = e != null ? e.getCritMin() : sp.crit()[0];
            double critMax = e != null ? e.getCritMax() : sp.crit()[1];
            out.add(new UmbralMetrica(sp.key(), sp.label(), sp.unit(), sp.dec(),
                    idealMin, idealMax, warnMin, warnMax, critMin, critMax));
        }
        return out;
    }

    private ConfiguracionOperativa buildOperativaDto(ConfiguracionOperativaEntity e) {
        return new ConfiguracionOperativa(
                e.getRiegoTiempoMaxSeg(), e.getRiegoVolMaxDiarioMl(), e.getInsumoDosisMax24hMl(),
                e.getMediasombraAperturaMaxPct(), e.getSeguimientoLatenciaMin(), e.getSeguimientoDeltaMin(),
                e.getUpdatedBy(), e.getUpdatedTs());
    }

    private List<RustificacionEtapa> buildRustificacionDto() {
        return rustificacionRepository.findAllByOrderByOrdenAsc().stream()
                .map(e -> new RustificacionEtapa(e.getOrden(), e.getDiaDesde(), e.getDiaHasta(), e.getAperturaPct()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @Transactional
    public Configuracion updateConfiguracion(Configuracion cfg, String usuario) {
        validar(cfg);

        // Umbrales: persistir las bandas (los metadatos no se guardan).
        for (UmbralMetrica u : cfg.umbrales()) {
            UmbralMetricaEntity e = umbralRepository.findById(u.key()).orElseGet(UmbralMetricaEntity::new);
            e.setMetricKey(u.key());
            e.setIdealMin(u.idealMin());
            e.setIdealMax(u.idealMax());
            e.setWarnMin(u.warnMin());
            e.setWarnMax(u.warnMax());
            e.setCritMin(u.critMin());
            e.setCritMax(u.critMax());
            umbralRepository.save(e);
        }

        // Operativa: fila única + auditoría (HU-15 CA-02).
        ConfiguracionOperativa op = cfg.operativa();
        ConfiguracionOperativaEntity oe = operativaRepository.findById(OPERATIVA_ID)
                .orElseGet(ConfiguracionOperativaEntity::new);
        oe.setId(OPERATIVA_ID);
        oe.setRiegoTiempoMaxSeg(op.riegoTiempoMaxSeg());
        oe.setRiegoVolMaxDiarioMl(op.riegoVolMaxDiarioMl());
        oe.setInsumoDosisMax24hMl(op.insumoDosisMax24hMl());
        oe.setMediasombraAperturaMaxPct(op.mediasombraAperturaMaxPct());
        oe.setSeguimientoLatenciaMin(op.seguimientoLatenciaMin());
        oe.setSeguimientoDeltaMin(op.seguimientoDeltaMin());
        oe.setUpdatedBy(usuario != null && !usuario.isBlank() ? usuario : "Ingeniero Agrónomo");
        oe.setUpdatedTs(System.currentTimeMillis());
        operativaRepository.save(oe);

        // Plan de rustificación: se reemplaza por completo, reindexando el orden.
        rustificacionRepository.deleteAllInBatch();
        List<RustificacionEtapa> etapas = cfg.rustificacion() != null ? cfg.rustificacion() : List.of();
        int orden = 1;
        for (RustificacionEtapa et : etapas) {
            rustificacionRepository.save(new RustificacionEtapaEntity(orden++, et.diaDesde(), et.diaHasta(), et.aperturaPct()));
        }

        invalidateCache();
        // Registro inmutable del cambio en el historial (HU-15 CA-02).
        historialService.registrarConfiguracion(oe.getUpdatedBy());
        return getConfiguracion();
    }

    // ------------------------------------------------------------------
    // Validación fisiológica (HU-15 CA-03..06)
    // ------------------------------------------------------------------

    private void validar(Configuracion cfg) {
        if (cfg == null) {
            throw new ConfiguracionInvalidaException("La configuración es obligatoria.");
        }
        validarUmbrales(cfg.umbrales());
        validarOperativa(cfg.operativa());
        validarRustificacion(cfg.rustificacion(),
                cfg.operativa() != null ? cfg.operativa().mediasombraAperturaMaxPct() : 100);
    }

    private void validarUmbrales(List<UmbralMetrica> umbrales) {
        if (umbrales == null || umbrales.size() != factory.size()) {
            throw new ConfiguracionInvalidaException(
                    "Se esperan los umbrales de las " + factory.size() + " métricas.");
        }
        for (UmbralMetrica u : umbrales) {
            MetricSpec f = factory.get(u.key());
            if (f == null) {
                throw new ConfiguracionInvalidaException("Métrica desconocida: " + u.key());
            }
            // Coherencia de bandas anidadas (HU-15 CA-03).
            boolean coherente = u.critMin() <= u.warnMin()
                    && u.warnMin() <= u.idealMin()
                    && u.idealMin() < u.idealMax()
                    && u.idealMax() <= u.warnMax()
                    && u.warnMax() <= u.critMax();
            if (!coherente) {
                throw new ConfiguracionInvalidaException(
                        "Bandas incoherentes para «" + f.label() + "»: debe cumplirse "
                                + "crit mín ≤ warn mín ≤ ideal mín < ideal máx ≤ warn máx ≤ crit máx.");
            }
            // Envelope fisiológico de fábrica (HU-15 CA-03).
            double envMin = f.crit()[0];
            double envMax = f.crit()[1];
            if (u.critMin() < envMin || u.critMax() > envMax) {
                throw new ConfiguracionInvalidaException(
                        "Valor fuera del rango fisiológico permitido para «" + f.label()
                                + "» (" + fmt(envMin) + " a " + fmt(envMax) + " " + f.unit() + ").");
            }
        }
    }

    private void validarOperativa(ConfiguracionOperativa op) {
        if (op == null) {
            throw new ConfiguracionInvalidaException("Faltan los límites operativos.");
        }
        requirePositive(op.riegoTiempoMaxSeg(), "el tiempo máximo de apertura de riego");
        requirePositive(op.riegoVolMaxDiarioMl(), "el volumen máximo diario de riego");
        requirePositive(op.insumoDosisMax24hMl(), "la dosis máxima de insumo por 24 h");
        requirePositive(op.seguimientoLatenciaMin(), "la latencia de seguimiento");
        requirePositive(op.seguimientoDeltaMin(), "el delta mínimo de recuperación");
        if (op.mediasombraAperturaMaxPct() <= 0 || op.mediasombraAperturaMaxPct() > 100) {
            throw new ConfiguracionInvalidaException(
                    "La apertura máxima de mediasombra debe estar entre 0 y 100 %.");
        }
    }

    private void validarRustificacion(List<RustificacionEtapa> etapas, double aperturaMax) {
        if (etapas == null || etapas.isEmpty()) {
            return; // el plan es opcional
        }
        List<RustificacionEtapa> ordenadas = new ArrayList<>(etapas);
        ordenadas.sort(Comparator.comparingInt(RustificacionEtapa::diaDesde));
        int prevHasta = 0;
        for (RustificacionEtapa et : ordenadas) {
            if (et.diaDesde() < 1 || et.diaDesde() > et.diaHasta()) {
                throw new ConfiguracionInvalidaException(
                        "Etapa de rustificación inválida: «día desde» debe ser ≥ 1 y ≤ «día hasta».");
            }
            if (et.diaDesde() <= prevHasta) {
                throw new ConfiguracionInvalidaException(
                        "Las etapas de rustificación no pueden solaparse en el cronograma.");
            }
            if (et.aperturaPct() < 0 || et.aperturaPct() > aperturaMax) {
                throw new ConfiguracionInvalidaException(
                        "La apertura de cada etapa debe estar entre 0 % y la apertura máxima ("
                                + fmt(aperturaMax) + " %).");
            }
            prevHasta = et.diaHasta();
        }
    }

    private void requirePositive(double value, String nombre) {
        if (value <= 0) {
            throw new ConfiguracionInvalidaException("El valor de " + nombre + " debe ser mayor a 0.");
        }
    }

    // ------------------------------------------------------------------
    // Integración con el motor (valores activos)
    // ------------------------------------------------------------------

    /** Specs efectivas: metadatos de fábrica + bandas persistidas. Cacheadas. */
    public List<MetricSpec> getEffectiveSpecs() {
        List<MetricSpec> cached = effectiveSpecsCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (effectiveSpecsCache == null) {
                effectiveSpecsCache = buildEffectiveSpecs();
            }
            return effectiveSpecsCache;
        }
    }

    private List<MetricSpec> buildEffectiveSpecs() {
        Map<String, UmbralMetricaEntity> persisted = new LinkedHashMap<>();
        for (UmbralMetricaEntity e : umbralRepository.findAll()) {
            persisted.put(e.getMetricKey(), e);
        }
        List<MetricSpec> out = new ArrayList<>();
        for (MetricSpec sp : SPECS) {
            UmbralMetricaEntity e = persisted.get(sp.key());
            if (e == null) {
                out.add(sp);
            } else {
                out.add(new MetricSpec(
                        sp.key(), sp.label(), sp.unit(),
                        new Double[]{e.getIdealMin(), e.getIdealMax()},
                        new Double[]{e.getWarnMin(), e.getWarnMax()},
                        new Double[]{e.getCritMin(), e.getCritMax()},
                        sp.dec(), sp.base()));
            }
        }
        return out;
    }

    /** Umbral mínimo de humedad de sustrato (idealMin) que dispara el riego. */
    public double getRiegoHumSusUmbral() {
        for (MetricSpec sp : getEffectiveSpecs()) {
            if ("humSus".equals(sp.key())) {
                return sp.ideal()[0];
            }
        }
        return 42.0; // fallback de fábrica
    }

    /**
     * Devuelve la entidad de configuración operativa para uso del motor de reglas.
     * Las reglas consumen esta entidad como input de solo lectura a través del {@link com.yerbanalytics.backend.engine.RuleContext}.
     */
    public ConfiguracionOperativaEntity getConfiguracionOperativa() {
        return loadOperativa();
    }

    /** Latencia de seguimiento en ms (HU-15 CA-07). */
    public long getLatencyMs() {
        return loadOperativa().getSeguimientoLatenciaMin() * 60_000L;
    }

    /** Etiqueta legible de la latencia (p. ej. "2 min"). */
    public String getLatencyLabel() {
        return loadOperativa().getSeguimientoLatenciaMin() + " min";
    }

    /** Delta mínimo de recuperación para catalogar una acción como efectiva. */
    public double getUmbralRecuperacion() {
        return loadOperativa().getSeguimientoDeltaMin();
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private ConfiguracionOperativaEntity loadOperativa() {
        ConfiguracionOperativaEntity cached = operativaCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (operativaCache == null) {
                operativaCache = operativaRepository.findById(OPERATIVA_ID).orElseGet(this::defaultOperativa);
            }
            return operativaCache;
        }
    }

    private ConfiguracionOperativaEntity defaultOperativa() {
        ConfiguracionOperativaEntity e = new ConfiguracionOperativaEntity();
        e.setId(OPERATIVA_ID);
        e.setRiegoTiempoMaxSeg(120);
        e.setRiegoVolMaxDiarioMl(2000);
        e.setInsumoDosisMax24hMl(15);
        e.setMediasombraAperturaMaxPct(100);
        e.setSeguimientoLatenciaMin(2);
        e.setSeguimientoDeltaMin(5);
        return e;
    }

    private synchronized void invalidateCache() {
        effectiveSpecsCache = null;
        operativaCache = null;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
