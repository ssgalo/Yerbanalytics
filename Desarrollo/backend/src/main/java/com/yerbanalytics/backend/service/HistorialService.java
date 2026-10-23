package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.ActionEvent;
import com.yerbanalytics.backend.dto.ColorPair;
import com.yerbanalytics.backend.effectiveness.EffectivenessKind;
import com.yerbanalytics.backend.effectiveness.FollowUp;
import com.yerbanalytics.backend.effectiveness.FollowUpPolicy;
import com.yerbanalytics.backend.effectiveness.TreatmentBaseline;
import com.yerbanalytics.backend.dto.Evolution;
import com.yerbanalytics.backend.dto.HistorialEvento;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import static com.yerbanalytics.backend.constant.NurseryConstants.*;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Registro, consulta y evaluación de efectividad del historial de acciones.
 * El historial es inmutable: este servicio sólo inserta y completa el seguimiento,
 * nunca borra ni permite editar la cadena de justificación de un evento.
 */
@Service
public class HistorialService {

    private static final String EMDASH = "—";
    /** Veredicto de un seguimiento que todavía no se evaluó. */
    public static final String VERDICT_EN_SEGUIMIENTO = "En seguimiento";

    private static final ColorPair VERDICT_EFECTIVA = new ColorPair("#E3F2E8", "#1E6B41");
    private static final ColorPair VERDICT_SEGUIMIENTO = new ColorPair("#E2ECF8", "#1F4F86");
    private static final ColorPair VERDICT_SIN = new ColorPair("#FBE3DD", "#9A2B17");

    private static final DateTimeFormatter FECHA_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZonaHorariaVivero.ZONA);

    private final HistorialRepository historialRepository;
    /** Cuánto se espera para juzgar cada acción (E-01…E-03). Perezoso: el servicio de efectividad también escribe en el historial. */
    private final FollowUpPolicy followUps;
    /** Reloj del vivero: el mismo que usa el despacho de riego para sellar y comparar los eventos. */
    private final Clock reloj;

    public HistorialService(HistorialRepository historialRepository,
                            @Lazy FollowUpPolicy followUps,
                            Clock reloj) {
        this.historialRepository = historialRepository;
        this.followUps = followUps;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Registro (invocado desde la lógica de actuación de NurseryService)
    // ------------------------------------------------------------------

    /** Registra un riego autónomo por sector, con seguimiento de humedad de sustrato. */
    public void registrarRiego(SectorEntity s) {
        Double humSus = currentMetric(s, "humSus");
        double hum = humSus != null ? humSus : 0.0;
        HistorialEventoEntity e = base(s, "Riego", "Efectiva");
        e.setLectura("Humedad de sustrato " + fmt0(hum) + "% bajo el umbral mínimo configurado.");
        e.setDecision("El motor de reglas ordena abrir la electroválvula del sector.");
        e.setAccion("Microaspersor abierto · riego autónomo en curso.");
        withIrrigationFollowUp(e, hum, 0);
        historialRepository.save(e);
    }

    /**
     * Registra el riego que el despacho acaba de ordenar al nodo: sector, volumen, duración, humedad
     * de sustrato y regla (HU-06 CA-05), con seguimiento de la humedad. {@code ts} es el instante de la
     * orden (el del reloj del vivero), no el de la escritura.
     */
    public void registrarRiego(SectorEntity s, DetalleRiego detalle, String regla, long ts) {
        HistorialEventoEntity e = base(s, "Riego", "Efectiva");
        e.setTs(ts);
        e.setRegla(regla);
        e.setVolumenL(detalle.volumenL());
        e.setDuracionSeg(detalle.duracionSeg());
        e.setLectura("Humedad de sustrato " + fmt0(detalle.humedad()) + "% (regla " + regla + ").");
        e.setDecision("El motor de reglas ordena regar " + fmtLitros(detalle.volumenL()) + " L durante "
                + detalle.duracionSeg() + " s"
                + (detalle.recortado() ? " (duración recortada al máximo de la válvula)." : "."));
        e.setAccion("Electroválvula abierta · riego autónomo en curso.");
        withIrrigationFollowUp(e, detalle.humedad(), detalle.duracionSeg());
        historialRepository.save(e);
    }

    /**
     * Registra una alerta del motor (INFO | WARNING | CRITICAL). Su alcance es la macro-zona, no un sector:
     * el evento queda con {@code sectorId = "—"} (igual que los de Configuración) y se filtra por zona.
     * {@code ts} es el instante de la evaluación (reloj del vivero).
     */
    public void registrarAlerta(String zonaId, String zonaName, String regla, DetalleAlerta detalle, long ts) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(EMDASH);
        e.setZonaId(zonaId);
        e.setZonaName(zonaName != null ? zonaName : zonaId);
        e.setTipo("Alerta");
        e.setTs(ts);
        e.setRegla(regla);
        e.setAlerta(detalle.nivel().name());
        e.setLectura("Alerta de la macro-zona " + zonaId + " (regla " + regla + ").");
        e.setDecision(detalle.texto());
        e.setAccion("Alerta " + detalle.nivel().name() + " registrada para el operador.");
        e.setRes("Informativo");
        e.setSev(EMDASH);
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /** Regla con la que queda en el historial un riego que se cortó antes de tiempo (ver {@link #registrarCorteRiego}). */
    public static final String REGLA_RIEGO_CORTADO = "RiegoCortado";

    /**
     * Deja constancia de que el riego de un sector se cortó antes de tiempo (OFF manual, protección del nodo): una
     * alerta INFO como las demás, pero con el {@code sectorId} real. Es lo que lee {@code DespachoRiego} al
     * reconstruir, tras un reinicio, qué válvulas siguen abiertas: un riego anterior a este corte no cuenta.
     */
    public void registrarCorteRiego(String zonaId, String zonaName, String sectorId, long ts) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(sectorId);
        e.setZonaId(zonaId);
        e.setZonaName(zonaName != null ? zonaName : zonaId);
        e.setTipo("Alerta");
        e.setTs(ts);
        e.setRegla(REGLA_RIEGO_CORTADO);
        e.setAlerta(NivelAlerta.INFO.name());
        e.setLectura("El nodo informó que el riego del sector " + sectorId + " se cortó (ack cancelado).");
        e.setDecision("Riego cortado antes de tiempo: algo cerró la válvula fuera del backend (comando manual o protección del nodo).");
        e.setAccion("El sector deja de figurar como regando y vuelve a poder regarse.");
        e.setRes("Informativo");
        e.setSev(EMDASH);
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /**
     * Registra una dosificación de insumo de {@code ml} mililitros. Guarda la duración de la bomba
     * ({@code ml × BOMBA_INSUMO_MS_POR_ML}) para que {@code SupplyDoseService} sepa cuándo la dosis ya terminó
     * aunque el nodo no mande el ack.
     */
    public void registrarInsumo(SectorEntity s, int ml) {
        HistorialEventoEntity e = armarInsumo(s);
        e.setDuracionSeg((int) Math.ceil(ml * ContratoNodo.BOMBA_INSUMO_MS_POR_ML / 1000.0));
        e.setDecision(e.getDecision() + " Dosis: " + ml + " ml.");
        historialRepository.save(e);
    }

    /**
     * Registra una aplicación de {@code ml} mililitros de un insumo concreto, pedida por {@code regla}. Es la fila
     * "Insumo" que lee la pausa de riego (R-06) y el seguimiento de efectividad. A diferencia de las otras variantes no
     * supone que la dispara un diagnóstico: la aplicación puede venir de un plan de nutrición.
     */
    public void registrarInsumo(SectorEntity s, int ml, String insumo, String regla) {
        registrarInsumo(s, ml, insumo, regla, null);
    }

    /**
     * Como la anterior, y si {@code followUp} no es nulo abre el seguimiento de efectividad del tratamiento (E-02, E-03). Sin él la
     * aplicación queda registrada pero no se evalúa (el plan de nutrición, o una racha que ya tiene su seguimiento abierto).
     */
    public void registrarInsumo(SectorEntity s, int ml, String insumo, String regla, FollowUp followUp) {
        HistorialEventoEntity e = armarInsumo(s);
        e.setRegla(regla);
        e.setDuracionSeg((int) Math.ceil(ml * ContratoNodo.BOMBA_INSUMO_MS_POR_ML / 1000.0));
        e.setLectura("Aplicación de " + insumo.toLowerCase() + " pedida por la regla " + regla + ".");
        e.setDecision("Las precondiciones de aplicación se cumplen: se ordenan " + ml + " ml de "
                + insumo.toLowerCase() + ".");
        if (followUp != null) {
            withTreatmentFollowUp(e, followUp);
        }
        historialRepository.save(e);
    }

    /** Registra una dosificación de insumo. Sin seguimiento: la evaluación de efectividad la abre quien aplica (E-02, E-03). */
    public void registrarInsumo(SectorEntity s) {
        historialRepository.save(armarInsumo(s));
    }

    private HistorialEventoEntity armarInsumo(SectorEntity s) {
        Double conf = s.getDiagnosisConf();
        HistorialEventoEntity e = base(s, "Insumo", "Efectiva");
        e.setLectura("Diagnóstico IA: " + s.getDiagnosisEstado()
                + (conf != null ? " (confianza " + fmt0(conf) + "%)" : "") + ".");
        e.setDecision("Confianza sobre el umbral: el motor habilita la dosificación localizada.");
        e.setAccion("Bomba peristáltica inyectando insumo en la línea del sector.");
        return e;
    }

    private HistorialEventoEntity base(SectorEntity s, String tipo, String res) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(s.getId());
        e.setZonaId(s.getZona().getId());
        e.setZonaName(s.getZona().getName());
        e.setTipo(tipo);
        e.setTs(reloj.millis());
        e.setRes(res);
        e.setSev(s.getDiagnosisSev());
        e.setBloqueoRepeticion(false);
        return e;
    }

    /**
     * E-01: el seguimiento de un riego compara la humedad de sustrato de la macro-zona antes del riego con la de la lectura de
     * verificación. Se evalúa {@code latencia de verificación} después de que cierra la válvula, no después de la orden.
     */
    private void withIrrigationFollowUp(HistorialEventoEntity e, double valorAntes, int valveSeconds) {
        long latency = valveSeconds * 1000L + followUps.latencyMs(EffectivenessKind.IRRIGATION);
        e.setEfectividadTipo(EffectivenessKind.IRRIGATION.name());
        e.setMetricKey("humSus");
        e.setValorAntes(valorAntes);
        e.setLatencyMs(latency);
        e.setEvoShow(true);
        e.setEvoMetric("Humedad de sustrato");
        e.setEvoUnit("%");
        e.setEvoAntes(fmt0(valorAntes));
        e.setEvoAhora("—");
        e.setEvoDelta("—");
        e.setEvoLatencia(followUps.latencyLabel(EffectivenessKind.IRRIGATION));
        e.setEvoVerdict(VERDICT_EN_SEGUIMIENTO);
        e.setEvoEvaluadoTs(null);
    }

    /**
     * E-02 y E-03: el seguimiento de un tratamiento compara el diagnóstico del sector de después con el de cuando se aplicó, que
     * queda en el evento (estado, severidad y los lindantes que ya tenían el daño).
     */
    private void withTreatmentFollowUp(HistorialEventoEntity e, FollowUp followUp) {
        TreatmentBaseline baseline = followUp.baseline();
        e.setRes("En seguimiento");
        e.setEfectividadTipo(followUp.kind().name());
        e.setDiagEstadoAntes(baseline.state());
        e.setVecinosAntes(String.join(",", new java.util.TreeSet<>(baseline.neighbors())));
        e.setLatencyMs(followUps.latencyMs(followUp.kind()));
        e.setEvoShow(true);
        e.setEvoMetric("Diagnóstico del sector");
        e.setEvoUnit("");
        e.setEvoAntes(baseline.severity() == null ? baseline.state() : baseline.state() + " " + baseline.severity());
        e.setEvoAhora("—");
        e.setEvoDelta("—");
        e.setEvoLatencia(followUps.latencyLabel(followUp.kind()));
        e.setEvoVerdict(VERDICT_EN_SEGUIMIENTO);
        e.setEvoEvaluadoTs(null);
    }

    /**
     * Registra una inacción deliberada del motor de reglas (Registro de Inacción).
     *
     * <p>Cuando el motor evalúa un sector y decide conscientemente NO actuar
     * (ej. "no riego porque va a llover", "no dosificación porque confianza < 70%"),
     * este método persiste el motivo en el historial. Permite que el usuario vea
     * por qué el sistema no actuó, distinguiéndolo de un fallo o desconexión.
     *
     * @param s        sector evaluado
     * @param type     tipo de acción (ej. NOOP_INFO, ABORT_ALL)
     * @param ruleName nombre de la regla que tomó la decisión
     * @param motivo   explicación legible de la decisión
     */
    public void registrarInaccion(SectorEntity s, com.yerbanalytics.backend.engine.ActionType type, String ruleName, String motivo) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(s.getId());
        e.setZonaId(s.getZona().getId());
        e.setZonaName(s.getZona().getName());
        e.setTipo("Info");
        e.setTs(reloj.millis());
        e.setLectura("Ciclo de evaluación: " + ruleName + ".");
        e.setDecision(motivo);
        e.setAccion(type.isBlocking() ? "Evaluación bloqueada." : "Condición normal — sin actuación.");
        e.setRes("Informativo");
        e.setSev(s.getDiagnosisSev());
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /**
     * Registra la reparación de un dispositivo averiado (S-05, HU-21 CA-04): quién la hizo, cuándo (el reloj del vivero) y qué
     * hizo. El evento es del sector del dispositivo, o de la macro-zona ({@code sectorId = "—"}) si es la malla.
     *
     * @param falla el tipo de falla que tenía marcada (p. ej. {@code SIN_CAUDAL})
     */
    @Transactional
    public void registrarReparacion(String usuario, String dispositivo, String sectorId, String zonaId, String zonaName,
                                    String falla, String detalle) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(sectorId);
        e.setZonaId(zonaId);
        e.setZonaName(zonaName != null ? zonaName : zonaId);
        e.setTipo("Reparación");
        e.setTs(reloj.millis());
        e.setLectura(dispositivo + " con falla registrada: " + NodeErrorType.describe(falla) + ".");
        e.setDecision("Reparación registrada por " + usuario + ": " + detalle);
        e.setAccion("Falla levantada: el dispositivo vuelve a lo autónomo en el siguiente pulso.");
        e.setRes("Efectiva");
        e.setSev(EMDASH);
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /**
     * Registra la revisión de un sector que había alcanzado el tope de fitosanitarios (F-06): quién la hizo, cuándo (el
     * reloj del vivero) y qué constató. Levanta el bloqueo de la siguiente aplicación.
     */
    @Transactional
    public void registrarRevisionFitosanitaria(String usuario, SectorEntity sector, String detalle) {
        ZonaEntity zona = sector.getZona();
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId(sector.getId());
        e.setZonaId(zona.getId());
        e.setZonaName(zona.getName() != null ? zona.getName() : zona.getId());
        e.setTipo("Revisión");
        e.setTs(reloj.millis());
        e.setLectura("Sector con el tope de fitosanitarios alcanzado.");
        e.setDecision("Revisión registrada por " + usuario + ": " + detalle);
        e.setAccion("Bloqueo levantado: el sector puede recibir fitosanitarios otra vez.");
        e.setRes("Efectiva");
        e.setSev(EMDASH);
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /**
     * Registra, de forma inmutable, un cambio de configuración agronómica (HU-15 CA-02):
     * deja asentado quién y cuándo recalibró el sistema.
     */
    @Transactional
    public void registrarConfiguracion(String usuario) {
        registrarConfiguracion(
                "Recalibración de parámetros agronómicos por " + usuario + ".",
                "Se validaron los nuevos umbrales y límites contra el rango fisiológico.",
                "Configuración actualizada: umbrales, límites operativos y plan de rustificación.");
    }

    /**
     * Variante con detalle: asienta qué se cambió (p. ej. las claves de los parámetros de reglas
     * modificados), para que el historial no diga sólo "se configuró algo".
     */
    @Transactional
    public void registrarConfiguracion(String usuario, String detalle) {
        registrarConfiguracion(
                "Recalibración de parámetros de reglas por " + usuario + ".",
                "Se validaron tipo, rango y restricciones cruzadas de los nuevos valores.",
                detalle);
    }

    /**
     * Recalibración del criterio de diagnóstico (bandas de severidad y orden de criticidad). Va con
     * su propio texto: no es la configuración agronómica ni el catálogo de parámetros del motor.
     */
    @Transactional
    public void registrarCriterioDiagnostico(String usuario, String detalle) {
        registrarConfiguracion(
                "Recalibración del criterio de diagnóstico por " + usuario + ".",
                "Se validaron las bandas de severidad (enteros, mínimo < media < alta) y el orden de criticidad.",
                detalle);
    }

    private void registrarConfiguracion(String lectura, String decision, String accion) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(UUID.randomUUID().toString());
        e.setSectorId("—");
        e.setZonaId("—");
        e.setZonaName("Sistema");
        e.setTipo("Configuración");
        e.setTs(reloj.millis());
        e.setLectura(lectura);
        e.setDecision(decision);
        e.setAccion(accion);
        e.setRes("Efectiva");
        e.setSev("—");
        e.setEvoShow(false);
        e.setBloqueoRepeticion(false);
        historialRepository.save(e);
    }

    /** Lectura vigente de la métrica: viene de la macro-zona del sector, no del sector. */
    private Double currentMetric(SectorEntity s, String key) {
        return s.getZona() != null ? s.getZona().raw(key) : null;
    }

    // ------------------------------------------------------------------
    // Consulta para el panel general (NurseryService)
    // ------------------------------------------------------------------

    /**
     * Devuelve las últimas {@code limit} acciones reales del historial (excluye Info/Configuración)
     * mapeadas al formato {@link ActionEvent} que espera el feed de actividad del dashboard.
     *
     * <p>Si el historial está vacío, retorna una lista vacía — el frontend muestra entonces
     * el mensaje "Sin actividad registrada".
     *
     * @param limit cantidad máxima de eventos a retornar
     */
    @Transactional(readOnly = true)
    public List<ActionEvent> getRecentActions(int limit) {
        return historialRepository.findRecentActions(limit)
                .stream()
                .map(e -> {
                    ColorPair rm = RES_MAP.getOrDefault(e.getRes(), new ColorPair("#ECEEEB", "#545C57"));
                    ActMeta meta = ACT.getOrDefault(e.getTipo(), ACT.get("Info"));
                    String titulo = e.getTipo() + " · " + e.getSectorId();
                    return new ActionEvent(
                            titulo,
                            e.getDecision() != null ? e.getDecision() : e.getLectura(),
                            formatAgo(e.getTs()),
                            e.getRes(),
                            rm.soft(),
                            rm.ink(),
                            meta.tint(),
                            meta.ink(),
                            meta.path()
                    );
                })
                .toList();
    }

    /**
     * Cuenta las acciones autónomas del día por tipo.
     *
     * @return mapa tipo → cantidad (ej. {"Riego": 12, "Insumo": 4, "Mediasombra": 2})
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countToday() {
        long startOfDay = java.time.LocalDate.now(reloj.withZone(ZonaHorariaVivero.ZONA))
                .atStartOfDay(ZonaHorariaVivero.ZONA)
                .toInstant()
                .toEpochMilli();
        return Map.of(
                "Riego",       historialRepository.countByTipoSinceTs("Riego",       startOfDay),
                "Insumo",      historialRepository.countByTipoSinceTs("Insumo",      startOfDay),
                "Mediasombra", historialRepository.countByTipoSinceTs("Mediasombra", startOfDay)
        );
    }

    // ------------------------------------------------------------------
    // Consulta (endpoint)
    // ------------------------------------------------------------------

    /** Eventos que devuelve la consulta si el cliente no pide un tope. */
    public static final int LIMITE_DEFAULT = 1000;
    /** Tope absoluto por consulta, pida lo que pida el cliente. */
    public static final int LIMITE_MAXIMO = 5000;

    /**
     * Los {@code limite} eventos más recientes que cumplen los filtros. Nunca el historial entero: con el motor
     * registrando inacciones de 600 sectores, una consulta sin tope llegó a cargar cientos de miles de filas en
     * memoria (y a serializarlas en un JSON de cientos de MB) y dejó colgados al backend y al navegador.
     *
     * @param limite tope pedido; {@code null} = {@link #LIMITE_DEFAULT}, y nunca más que {@link #LIMITE_MAXIMO}
     */
    @Transactional(readOnly = true)
    public List<HistorialEvento> getHistorial(String sector, String zona, String tipo, Long desde, Long hasta,
                                              Integer limite) {
        int tope = limite == null ? LIMITE_DEFAULT : Math.max(1, Math.min(LIMITE_MAXIMO, limite));
        return historialRepository.findFiltered(blank(sector), blank(zona), blank(tipo), desde, hasta,
                        PageRequest.of(0, tope))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private HistorialEvento toDto(HistorialEventoEntity e) {
        ColorPair rm = RES_MAP.getOrDefault(e.getRes(), new ColorPair("#ECEEEB", "#545C57"));
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
                    vc.ink(),
                    e.getEvoMotivo(),
                    e.isBloqueoRepeticion() && e.getEfectividadTipo() != null
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
                evo,
                e.getRegla(),
                e.getAlerta(),
                e.getVolumenL(),
                e.getDuracionSeg()
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

    /** Litros con coma decimal y sin ceros de más: 4,2 · 6 · 4,02. */
    private static String fmtLitros(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String fmt0(double v) {
        return String.format(Locale.US, "%.0f", v);
    }

    private String formatAgo(long timestamp) {
        long diffMs = Math.max(0, reloj.millis() - timestamp);
        long diffSec = diffMs / 1000;
        if (diffSec < 60) return "hace " + diffSec + " s";
        long diffMin = diffSec / 60;
        if (diffMin < 60) return "hace " + diffMin + " min";
        long diffH = diffMin / 60;
        if (diffH < 24) return "hace " + diffH + " h";
        return "hace " + (diffH / 24) + " d";
    }
}
