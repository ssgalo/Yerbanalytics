package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.mediasombra.VentanaDiaria;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.sensing.SensingCadence;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.UmbralMetricaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import static com.yerbanalytics.backend.constant.NurseryConstants.SPECS;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuración agronómica (HU-15): lectura, validación y persistencia de umbrales,
 * límites operativos y horario por defecto de la mediasombra. Es la fuente de verdad en runtime para
 * los umbrales de métricas ({@link #getEffectiveSpecs()}) y los parámetros de
 * seguimiento post-acción que consumen {@code NurseryService} e {@code HistorialService}.
 */
@Service
public class ConfiguracionService {

    /** Identidad de la fila única de configuración operativa. */
    private static final int OPERATIVA_ID = 1;

    /** Hora de fábrica de la pasada diaria del riel. */
    public static final String PASADA_HORA_FABRICA = "11:00";
    /** Horario por defecto de la mediasombra de fábrica (abierta de 08:00 a 17:00). */
    public static final String MEDIASOMBRA_DESDE_FABRICA = "08:00";
    public static final String MEDIASOMBRA_HASTA_FABRICA = "17:00";
    private static final Pattern HORA_HH_MM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final UmbralMetricaRepository umbralRepository;
    private final ConfiguracionOperativaRepository operativaRepository;
    private final HistorialService historialService;
    private final CatalogoParametrosService catalogoParametros;

    /** Metadatos de fábrica por clave de métrica (label, unit, dec, base, envelope). */
    private final Map<String, MetricSpec> factory;

    /** Cache de las specs efectivas (10 filas, camino caliente de telemetría). */
    private volatile List<MetricSpec> effectiveSpecsCache;
    /** Cache de los puntos de alerta por métrica. */
    private volatile Map<String, double[]> alertPointsCache;
    /** Cache de la configuración operativa. */
    private volatile ConfiguracionOperativaEntity operativaCache;

    public ConfiguracionService(UmbralMetricaRepository umbralRepository,
                                ConfiguracionOperativaRepository operativaRepository,
                                @Lazy HistorialService historialService,
                                CatalogoParametrosService catalogoParametros) {
        this.umbralRepository = umbralRepository;
        this.operativaRepository = operativaRepository;
        this.historialService = historialService;
        this.catalogoParametros = catalogoParametros;

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
        return new Configuracion(buildUmbralesDto(), buildOperativaDto(loadOperativa()));
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
            double[] alerta = puntosDeAlerta(e, sp);
            out.add(new UmbralMetrica(sp.key(), sp.label(), sp.unit(), sp.dec(),
                    idealMin, idealMax, warnMin, warnMax, critMin, critMax,
                    !Boolean.TRUE.equals(sp.afectaEstado()), alerta[0], alerta[1]));
        }
        return out;
    }

    private ConfiguracionOperativa buildOperativaDto(ConfiguracionOperativaEntity e) {
        return new ConfiguracionOperativa(
                e.getInsumoDosisMax24hMl(),
                e.getSeguimientoLatenciaMin(), e.getSeguimientoDeltaMin(),
                e.getIntervaloSensadoMinutos() != null ? e.getIntervaloSensadoMinutos() : SensingCadence.DEFAULT_MINUTES,
                e.getUpdatedBy(), e.getUpdatedTs(),
                pasadaDiariaActiva(e), pasadaDiariaHora(e),
                (int) Math.ceil(catalogoParametros.vigentes().numero(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA)),
                mediasombraDesde(e), mediasombraHasta(e));
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
            // Sin punto en el payload queda nulo: vale el medio de la franja vigente.
            e.setAlertMin(u.alertMin());
            e.setAlertMax(u.alertMax());
            umbralRepository.save(e);
        }

        // Operativa: fila única + auditoría (HU-15 CA-02).
        ConfiguracionOperativa op = cfg.operativa();
        ConfiguracionOperativaEntity oe = operativaRepository.findById(OPERATIVA_ID)
                .orElseGet(ConfiguracionOperativaEntity::new);
        oe.setId(OPERATIVA_ID);
        oe.setInsumoDosisMax24hMl(op.insumoDosisMax24hMl());
        oe.setSeguimientoLatenciaMin(op.seguimientoLatenciaMin());
        oe.setSeguimientoDeltaMin(op.seguimientoDeltaMin());
        oe.setIntervaloSensadoMinutos(op.intervaloSensadoMinutos());
        if (op.pasadaDiariaActiva() != null) {
            oe.setPasadaDiariaActiva(op.pasadaDiariaActiva());
        }
        if (op.pasadaDiariaHora() != null) {
            oe.setPasadaDiariaHora(op.pasadaDiariaHora());
        }
        if (op.mediasombraAbiertaDesde() != null) {
            oe.setMediasombraAbiertaDesde(op.mediasombraAbiertaDesde());
        }
        if (op.mediasombraAbiertaHasta() != null) {
            oe.setMediasombraAbiertaHasta(op.mediasombraAbiertaHasta());
        }
        oe.setUpdatedBy(usuario != null && !usuario.isBlank() ? usuario : "Ingeniero Agrónomo");
        oe.setUpdatedTs(System.currentTimeMillis());
        operativaRepository.save(oe);

        invalidateCache();
        // The effective stale threshold depends on the pulse interval: drop the rules snapshot now and again after commit.
        catalogoParametros.invalidarAlTerminar();
        // Registro inmutable del cambio en el historial (HU-15 CA-02).
        historialService.registrarConfiguracion(oe.getUpdatedBy());
        return getConfiguracion();
    }

    // ------------------------------------------------------------------
    // Validación fisiológica (HU-15 CA-03..06)
    // ------------------------------------------------------------------

    private void validar(Configuracion cfg) {
        if (cfg == null) {
            throw new InvalidConfigurationException("La configuración es obligatoria.");
        }
        validarUmbrales(cfg.umbrales());
        validarOperativa(cfg.operativa());
    }

    private void validarUmbrales(List<UmbralMetrica> umbrales) {
        if (umbrales == null || umbrales.size() != factory.size()) {
            throw new InvalidConfigurationException(
                    "Se esperan los umbrales de las " + factory.size() + " métricas.");
        }
        for (UmbralMetrica u : umbrales) {
            MetricSpec f = factory.get(u.key());
            if (f == null) {
                throw new InvalidConfigurationException("Métrica desconocida: " + u.key());
            }
            // Coherencia de bandas anidadas (HU-15 CA-03).
            boolean coherente = u.critMin() <= u.warnMin()
                    && u.warnMin() <= u.idealMin()
                    && u.idealMin() < u.idealMax()
                    && u.idealMax() <= u.warnMax()
                    && u.warnMax() <= u.critMax();
            if (!coherente) {
                throw new InvalidConfigurationException(
                        "Bandas incoherentes para «" + f.label() + "»: debe cumplirse "
                                + "crit mín ≤ warn mín ≤ ideal mín < ideal máx ≤ warn máx ≤ crit máx.");
            }
            boolean alertaCoherente = (u.alertMin() == null
                    || (u.warnMin() <= u.alertMin() && u.alertMin() <= u.idealMin()))
                    && (u.alertMax() == null
                    || (u.idealMax() <= u.alertMax() && u.alertMax() <= u.warnMax()));
            if (!alertaCoherente) {
                throw new InvalidConfigurationException(
                        "Bandas incoherentes para «" + f.label() + "»: cada punto de alerta debe "
                                + "quedar entre el crítico y el ideal de su lado.");
            }
            // Envelope fisiológico de fábrica (HU-15 CA-03).
            double envMin = f.crit()[0];
            double envMax = f.crit()[1];
            if (u.critMin() < envMin || u.critMax() > envMax) {
                throw new InvalidConfigurationException(
                        "Valor fuera del rango fisiológico permitido para «" + f.label()
                                + "» (" + fmt(envMin) + " a " + fmt(envMax) + " " + f.unit() + ").");
            }
        }
    }

    private void validarOperativa(ConfiguracionOperativa op) {
        if (op == null) {
            throw new InvalidConfigurationException("Faltan los límites operativos.");
        }
        requirePositive(op.insumoDosisMax24hMl(), "la dosis máxima de insumo por 24 h");
        requirePositive(op.seguimientoLatenciaMin(), "la latencia de seguimiento");
        requirePositive(op.seguimientoDeltaMin(), "el delta mínimo de recuperación");
        // Intervalo del pulso de sensado y del ciclo de lectura del riego. Una base vieja con un valor fuera de
        // rango se sigue leyendo acotado (SensingCadence.intervalMinutes); lo que no se acepta es guardarlo.
        int intervalo = op.intervaloSensadoMinutos();
        if (intervalo < SensingCadence.MIN_MINUTES || intervalo > SensingCadence.MAX_MINUTES) {
            throw new InvalidConfigurationException("intervaloSensadoMinutos: el intervalo de sensado y evaluación "
                    + "debe estar entre " + SensingCadence.MIN_MINUTES + " y " + SensingCadence.MAX_MINUTES + " min.");
        }
        if (op.pasadaDiariaHora() != null && !horaValida(op.pasadaDiariaHora())) {
            throw new InvalidConfigurationException("pasadaDiariaHora: la hora de la pasada diaria debe tener el "
                    + "formato HH:mm, entre 00:00 y 23:59.");
        }
        validarHorarioMediasombra(op.mediasombraAbiertaDesde(), op.mediasombraAbiertaHasta());
    }

    /**
     * Horario por defecto de la mediasombra: las dos horas juntas (o ninguna, que conserva el guardado), {@code HH:mm} y
     * el cierre después de la apertura (no cruza la medianoche).
     */
    private void validarHorarioMediasombra(String desde, String hasta) {
        if (desde == null && hasta == null) {
            return;
        }
        if (!horaValida(desde) || !horaValida(hasta)) {
            throw new InvalidConfigurationException("mediasombraAbiertaDesde / mediasombraAbiertaHasta: el horario de "
                    + "la mediasombra debe tener el formato HH:mm, entre 00:00 y 23:59.");
        }
        if (!LocalTime.parse(hasta).isAfter(LocalTime.parse(desde))) {
            throw new InvalidConfigurationException("mediasombraAbiertaHasta: la hora de cierre de la mediasombra debe "
                    + "ser posterior a la de apertura.");
        }
    }

    private void requirePositive(double value, String nombre) {
        if (value <= 0) {
            throw new InvalidConfigurationException("El valor de " + nombre + " debe ser mayor a 0.");
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
                        sp.dec(), sp.base(), sp.grupo(), sp.afectaEstado()));
            }
        }
        return out;
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
        e.setInsumoDosisMax24hMl(15);
        e.setSeguimientoLatenciaMin(2);
        e.setSeguimientoDeltaMin(5);
        e.setIntervaloSensadoMinutos(SensingCadence.DEFAULT_MINUTES);
        return e;
    }

    /** Pasada diaria activa; una fila sin valor se lee como desactivada (de fábrica). */
    public static boolean pasadaDiariaActiva(ConfiguracionOperativaEntity e) {
        return Boolean.TRUE.equals(e.getPasadaDiariaActiva());
    }

    /** Hora de la pasada diaria ({@code HH:mm}); sin valor, o con uno inválido en la base, la de fábrica. */
    public static String pasadaDiariaHora(ConfiguracionOperativaEntity e) {
        String hora = e.getPasadaDiariaHora();
        return hora != null && horaValida(hora) ? hora : PASADA_HORA_FABRICA;
    }

    /** Apertura del horario por defecto de la mediasombra ({@code HH:mm}); sin valor válido, la de fábrica. */
    public static String mediasombraDesde(ConfiguracionOperativaEntity e) {
        String desde = e.getMediasombraAbiertaDesde();
        String hasta = e.getMediasombraAbiertaHasta();
        return horarioValido(desde, hasta) ? desde : MEDIASOMBRA_DESDE_FABRICA;
    }

    /** Cierre del horario por defecto de la mediasombra ({@code HH:mm}); sin valor válido, el de fábrica. */
    public static String mediasombraHasta(ConfiguracionOperativaEntity e) {
        String desde = e.getMediasombraAbiertaDesde();
        String hasta = e.getMediasombraAbiertaHasta();
        return horarioValido(desde, hasta) ? hasta : MEDIASOMBRA_HASTA_FABRICA;
    }

    /** El horario por defecto de la mediasombra vigente (el guardado o el de fábrica). */
    public VentanaDiaria horarioMediasombraPorDefecto() {
        ConfiguracionOperativaEntity e = loadOperativa();
        return new VentanaDiaria(LocalTime.parse(mediasombraDesde(e)), LocalTime.parse(mediasombraHasta(e)));
    }

    private static boolean horarioValido(String desde, String hasta) {
        return horaValida(desde) && horaValida(hasta) && LocalTime.parse(hasta).isAfter(LocalTime.parse(desde));
    }

    /** {@code HH:mm} con dos dígitos en cada parte, de 00:00 a 23:59. */
    static boolean horaValida(String hora) {
        return hora != null && HORA_HH_MM.matcher(hora).matches();
    }

    private synchronized void invalidateCache() {
        effectiveSpecsCache = null;
        alertPointsCache = null;
        operativaCache = null;
    }

    /** Puntos de alerta {bajo, alto} de una fila, o los de fábrica si no hay fila; nulos = medio de la franja. */
    private static double[] puntosDeAlerta(UmbralMetricaEntity e, MetricSpec sp) {
        double idealMin = e != null ? e.getIdealMin() : sp.ideal()[0];
        double idealMax = e != null ? e.getIdealMax() : sp.ideal()[1];
        double warnMin = e != null ? e.getWarnMin() : sp.warn()[0];
        double warnMax = e != null ? e.getWarnMax() : sp.warn()[1];
        Double bajo = e != null ? e.getAlertMin() : null;
        Double alto = e != null ? e.getAlertMax() : null;
        return new double[]{
                bajo != null ? bajo : UmbralMetrica.puntoMedio(warnMin, idealMin, sp.dec()),
                alto != null ? alto : UmbralMetrica.puntoMedio(idealMax, warnMax, sp.dec()),
        };
    }

    /**
     * Puntos de alerta vigentes por clave de métrica ({bajo, alto}). Sólo deciden si se emite una
     * alerta; el estado lo siguen fijando las bandas de {@link #getEffectiveSpecs()}.
     */
    public Map<String, double[]> getAlertPoints() {
        Map<String, double[]> cached = alertPointsCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (alertPointsCache == null) {
                Map<String, UmbralMetricaEntity> persisted = new LinkedHashMap<>();
                for (UmbralMetricaEntity e : umbralRepository.findAll()) {
                    persisted.put(e.getMetricKey(), e);
                }
                Map<String, double[]> out = new LinkedHashMap<>();
                for (MetricSpec sp : SPECS) {
                    out.put(sp.key(), puntosDeAlerta(persisted.get(sp.key()), sp));
                }
                alertPointsCache = out;
            }
            return alertPointsCache;
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
