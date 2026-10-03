package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Despacho de riego por tandas (design D4 de implement-reglas-riego): las reglas DECIDEN y encolan
 * en la {@link ColaRiego}; este servicio EJECUTA, de a {@code riego.sectores-simultaneos} válvulas
 * abiertas por macro-zona, en orden de numeración de sector.
 *
 * <p>Cada {@code tick} (por defecto cada 10 s, {@code yerbanalytics.riego.despacho-intervalo-ms}):
 * <ol>
 *   <li>da por cerrados los riegos cuyo {@code ts + duración + 5 s} ya pasó;</li>
 *   <li>por zona, <b>revalida con datos actuales</b> (ver abajo), descarta lo pendiente con bloqueo manual
 *       (del sector o de la zona) o que ya está regando, y abre los sectores que entren en el cupo libre:
 *       publica {@code valve ON durationSec} y registra el riego en el historial.</li>
 * </ol>
 *
 * <p><b>Revalidación (S-02, R-05):</b> una solicitud encolada es una decisión vieja (una ronda de 100 sectores
 * tarda ~80 min en despacharse). Las reglas la completan aunque la humedad se recupere, así que acá se mira el
 * mundo de AHORA antes de abrir cada válvula: bloqueo manual; lectura y humedad de la zona vigentes (mismo
 * parámetro y misma definición que {@code StaleSensorRule}: si el nodo murió con 90 sectores en cola, no se abre
 * ninguno); humedad actual menor que {@code riego.saturacion-bloqueo}; y, para las de R-01, hora dentro de
 * {@code riego.ventana-normal} (cerrada al minuto). R-02 no depende de la ventana. Lo que no pasa se descarta de
 * la cola, se loguea y deja UNA alerta WARNING por zona y motivo.
 *
 * <p><b>"Regando" no es un estado guardado:</b> es "existe un riego cuyo {@code ts + duración + 5 s}
 * es mayor que ahora". Coincide con el principio 6 de {@code reglas_v2} (el ESP32 corta solo al cumplir
 * la duración) y no necesita el ACK. El margen de 5 s cubre la latencia del comando para no abrir el
 * sector N+1 antes de que cierre el N. Tras un reinicio se reconstruye del historial; lo pendiente se
 * pierde y la próxima telemetría lo vuelve a decidir.
 *
 * <p><b>Concurrencia:</b> la cola la tocan el hilo MQTT, este scheduler y los controllers; el mapa de
 * riegos en curso es concurrente (lo lee el snapshot del dashboard) y el tick está serializado.
 */
@Service
public class DespachoRiego implements ConsumidorParametros {

    private static final Logger log = LoggerFactory.getLogger(DespachoRiego.class);
    private static final String NAME = "DespachoRiego";

    /** Margen tras la duración, para la latencia del comando y del cierre. */
    static final long MARGEN_MS = 5_000L;
    /** Lo más atrás que hay que mirar el historial: un riego dura a lo sumo la duración máxima más el margen. */
    private static final long VENTANA_RECONSTRUCCION_MS = ContratoNodo.DURACION_VALVULA_MAX_SEG * 1000L + MARGEN_MS;

    /** Riego en curso de un sector: de qué zona es y cuándo se da por cerrado. */
    private record Curso(String zonaId, long finMs) {
    }

    private final ColaRiego cola;
    private final ComandoActuadorPublisher publisher;
    private final HistorialService historialService;
    private final HistorialRepository historialRepository;
    private final SectorRepository sectorRepository;
    private final ZonaRepository zonaRepository;
    private final ManualLockRepository manualLockRepository;
    private final CatalogoParametrosService parametros;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    private final Map<String, Curso> enCurso = new ConcurrentHashMap<>();
    /**
     * Último riego despachado por sector (epoch ms) y último ordenado por R-02, en memoria: si el historial falla
     * al registrar, pasada la duración el ciclo ya no vería ese riego y R-01 podría repetirlo. Se combina con lo
     * que sale del historial al armar el contexto de riego. Acotado por la cantidad de sectores.
     */
    private final Map<String, Long> ultimoRiego = new ConcurrentHashMap<>();
    private final Map<String, Long> ultimoRiegoCritico = new ConcurrentHashMap<>();
    private volatile boolean reconstruido;

    public DespachoRiego(ColaRiego cola,
                         ComandoActuadorPublisher publisher,
                         HistorialService historialService,
                         HistorialRepository historialRepository,
                         SectorRepository sectorRepository,
                         ZonaRepository zonaRepository,
                         ManualLockRepository manualLockRepository,
                         // Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros)
                         // para armarse, y este necesita el catálogo para leer el valor vigente.
                         @Lazy CatalogoParametrosService parametros,
                         Clock reloj,
                         PlatformTransactionManager transacciones) {
        this.cola = cola;
        this.publisher = publisher;
        this.historialService = historialService;
        this.historialRepository = historialRepository;
        this.sectorRepository = sectorRepository;
        this.zonaRepository = zonaRepository;
        this.manualLockRepository = manualLockRepository;
        this.parametros = parametros;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.SECTORES_SIMULTANEOS,
                // La revalidación al despachar: los mismos parámetros que las reglas que ya decidieron.
                ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA, ParametrosRiego.SATURACION_BLOQUEO,
                ParametrosRiego.VENTANA_NORMAL);
    }

    /** Lee un parámetro del catálogo, y sólo uno de los que declara: misma garantía que {@code Evaluacion}. */
    double numero(DefinicionParametro p) {
        if (!parametros().contains(p)) {
            throw new ParametroNoDeclaradoException(NAME, p.clave());
        }
        return parametros.vigentes().numero(p);
    }

    /** Ventana horaria vigente de un parámetro del catálogo, y sólo de los que declara. */
    VentanaHoraria ventana(DefinicionParametro p) {
        if (!parametros().contains(p)) {
            throw new ParametroNoDeclaradoException(NAME, p.clave());
        }
        return parametros.vigentes().ventana(p);
    }

    /** Epoch ms del último riego despachado al sector desde que arrancó el proceso, o {@code null}. */
    public Long ultimoRiegoMs(String sectorId) {
        return ultimoRiego.get(sectorId);
    }

    /** Epoch ms del último riego de R-02 despachado al sector desde que arrancó el proceso, o {@code null}. */
    public Long ultimoRiegoCriticoMs(String sectorId) {
        return ultimoRiegoCritico.get(sectorId);
    }

    /**
     * Estado de la válvula del sector para el dashboard: {@code Regando} | {@code En cola} |
     * {@code Cerrada}. Barato (mapas en memoria): el snapshot lo pide para los 600 sectores.
     */
    public String estadoValvula(String sectorId) {
        asegurarReconstruido();
        Curso c = enCurso.get(sectorId);
        if (c != null && c.finMs() > reloj.millis()) {
            return "Regando";
        }
        return cola.contiene(sectorId) ? "En cola" : "Cerrada";
    }

    /**
     * Cuándo termina (epoch ms, con el margen incluido) el riego en curso del sector, o {@code null} si no
     * tiene uno. Lo usa la regla de ciclo de lectura para no regar un sector que todavía está abierto.
     */
    public Long finRiegoEnCurso(String sectorId) {
        asegurarReconstruido();
        Curso c = enCurso.get(sectorId);
        return c != null && c.finMs() > reloj.millis() ? c.finMs() : null;
    }

    /**
     * Olvida lo pendiente y lo que estaba regando (en memoria): se llama al regenerar la topología, cuando
     * los sectores del vivero anterior ya no existen o sus ids se van a reutilizar.
     */
    public synchronized void reiniciarEstado() {
        cola.limpiar();
        enCurso.clear();
        ultimoRiego.clear();
        ultimoRiegoCritico.clear();
    }

    /**
     * Sin {@code @Transactional}: una transacción abierta durante el tick mantendría una conexión JDBC
     * mientras se publica por MQTT, y el historial de las válvulas YA abiertas se confirmaría recién al final
     * (si algo fallara en el medio se perdería, y tras un reinicio el sector podría regarse otra vez). Cada
     * riego se registra en su propia transacción, apenas después de publicarlo.
     *
     * <p>Corre en su propio scheduler ({@code despachoScheduler}): el barrido del watchdog (600 sectores y el
     * HTTP del pronóstico con reintentos) no puede demorar la liberación de cupo de las tandas.
     */
    @Scheduled(scheduler = "despachoScheduler", fixedDelayString = "${yerbanalytics.riego.despacho-intervalo-ms:10000}")
    public synchronized void tick() {
        long ahora = reloj.millis();
        Set<String> zonas = cola.zonas();
        if (zonas.isEmpty()) {
            return;   // nada que despachar: ni siquiera se consulta la base
        }
        if (!reconstruir(ahora)) {
            return;   // sin saber qué válvulas siguen abiertas no se abre ninguna
        }
        purgar(ahora);

        int cupo = (int) numero(ParametrosRiego.SECTORES_SIMULTANEOS);
        List<ManualLockEntity> bloqueos = manualLockRepository.findByActiveTrue();
        Set<String> sectoresBloqueados = bloqueos.stream().map(ManualLockEntity::getSectorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> zonasBloqueadas = bloqueos.stream().map(ManualLockEntity::getZonaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        for (String zonaId : zonas) {
            despacharZona(zonaId, ahora, cupo, zonasBloqueadas.contains(zonaId), sectoresBloqueados);
        }
    }

    private void despacharZona(String zonaId, long ahora, int cupo, boolean zonaBloqueada, Set<String> sectoresBloqueados) {
        // Los datos de AHORA, no los que vieron las reglas al decidir. Sin poder leerlos no se abre nada (y no se
        // descarta: la solicitud sigue para el próximo tick).
        ZonaEntity zona;
        try {
            zona = zonaRepository.findById(zonaId).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Zona {}: no se pudo leer la lectura vigente ({}); no se abre ninguna válvula en este tick.",
                    zonaId, e.getMessage());
            return;
        }
        String motivoZona = motivoDeZonaNoVigente(zona, ahora);
        // La copia de la cola se toma DESPUÉS de leer la zona y lo más cerca posible del reclamo de cada solicitud:
        // cuanto más vieja la copia, más chances de que la telemetría la haya reemplazado (y no se abre ese tick).
        List<SolicitudRiego> pendientes = cola.pendientes(zonaId);
        if (pendientes.isEmpty()) {
            return;
        }
        if (motivoZona != null) {
            for (SolicitudRiego s : pendientes) {
                cola.retirarSiCoincide(s);
            }
            descartadas(zonaId, zona, ahora, Map.of(motivoZona, pendientes.size()));
            return;
        }
        boolean enVentana = ventana(ParametrosRiego.VENTANA_NORMAL)
                .contieneHastaElMinuto(LocalTime.ofInstant(reloj.instant(), ZonaHorariaVivero.ZONA));
        Map<String, Integer> descartes = new LinkedHashMap<>();

        long abiertos = enCurso.values().stream().filter(c -> c.zonaId().equals(zonaId)).count();
        long libres = cupo - abiertos;

        for (SolicitudRiego s : pendientes) {
            if (zonaBloqueada || sectoresBloqueados.contains(s.sectorId())) {
                log.info("Sector {}: riego descartado, hay un bloqueo manual activo.", s.sectorId());
                cola.retirarSiCoincide(s);
                continue;
            }
            if (enCurso.containsKey(s.sectorId())) {
                log.debug("Sector {}: ya está regando, se descarta la solicitud repetida.", s.sectorId());
                cola.retirarSiCoincide(s);
                continue;
            }
            if (!enVentana && !s.esDeficitCritico()) {
                // R-05: de noche sólo riega R-02. Se mira aunque no haya cupo: lo vencido no espera.
                if (cola.retirarSiCoincide(s)) {
                    descartes.merge(MOTIVO_FUERA_DE_VENTANA, 1, Integer::sum);
                }
                continue;
            }
            if (libres <= 0) {
                continue;   // sigue revisando: lo bloqueado o vencido se descarta aunque no haya cupo
            }
            if (sectorRepository.findById(s.sectorId()).isEmpty()) {
                log.warn("Sector {}: ya no existe, se descarta su solicitud de riego.", s.sectorId());
                cola.retirarSiCoincide(s);
                continue;
            }
            // Los bloqueos se leyeron al empezar el tick: un operario pudo bloquear después. Se mira de nuevo,
            // justo antes de abrir ESTE sector.
            if (hayBloqueoAhora(zonaId, s.sectorId())) {
                log.info("Sector {}: riego descartado, hay un bloqueo manual activo.", s.sectorId());
                cola.retirarSiCoincide(s);
                continue;
            }
            // Reclamo: la saca de la cola sólo si sigue siendo la MISMA solicitud. Si la telemetría la retiró
            // (R-04, bloqueo) o la reemplazó después de que se copió la cola, no se publica nada.
            if (!cola.retirarSiCoincide(s)) {
                log.debug("Sector {}: la solicitud cambió o se canceló mientras se despachaba; no se abre.", s.sectorId());
                continue;
            }
            DetalleRiego d = s.detalle();
            ComandoActuadorPublisher.Resultado r = publisher.publicar(zonaId, s.sectorId(), "valve", "ON",
                    Map.of("durationSec", d.duracionSeg()));
            if (!r.publicado()) {
                // Sin broker no se registra el riego y la solicitud sigue: el próximo tick reintenta.
                cola.reponerSiAusente(s);
                log.warn("Zona {}: no se pudo abrir {} ({}). Se reintenta en el próximo ciclo.",
                        zonaId, s.sectorId(), r.error());
                descartadas(zonaId, zona, ahora, descartes);
                return;
            }
            // La válvula ya abrió: se marca en curso ANTES de escribir el historial. Si el registro falla, el
            // sector sigue "regando" en memoria y no se vuelve a publicar, y el ciclo recuerda el riego.
            enCurso.put(s.sectorId(), new Curso(zonaId, ahora + d.duracionSeg() * 1000L + MARGEN_MS));
            ultimoRiego.merge(s.sectorId(), ahora, Math::max);
            if (s.esDeficitCritico()) {
                ultimoRiegoCritico.merge(s.sectorId(), ahora, Math::max);
            }
            libres--;
            registrar(s, ahora);
        }
        descartadas(zonaId, zona, ahora, descartes);
    }

    private static final String MOTIVO_FUERA_DE_VENTANA =
            "el riego por déficit común quedó fuera de la ventana horaria de riego";

    /**
     * Por qué NO se puede abrir ninguna válvula de la zona con los datos de ahora, o {@code null} si se puede.
     * La lectura y la humedad se miden como {@code StaleSensorRule}; la saturación, contra el mismo bloqueo de R-04.
     */
    private String motivoDeZonaNoVigente(ZonaEntity zona, long ahora) {
        if (zona == null) {
            return "la macro-zona ya no existe";
        }
        double max = numero(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA);
        Double lectura = FrescuraLectura.antiguedadSegundos(zona, ahora);
        if (lectura == null || lectura > max) {
            return "el nodo testigo no reportó dentro del umbral de antigüedad";
        }
        Double antiguedadHumedad = FrescuraLectura.antiguedadHumedadSegundos(zona, ahora);
        if (antiguedadHumedad == null || antiguedadHumedad > max) {
            return "la humedad de sustrato no se actualizó dentro del umbral de antigüedad (¿falla de la sonda?)";
        }
        Double humedad = zona.getHumSusRaw();
        if (humedad == null || humedad.isNaN()) {
            return "sin lectura de humedad de sustrato";
        }
        if (humedad >= numero(ParametrosRiego.SATURACION_BLOQUEO)) {
            return "el sustrato ya está en o sobre el umbral de saturación";
        }
        return null;
    }

    /** Deja constancia (log y una alerta WARNING por zona y motivo) de lo que se descartó por revalidación. */
    private void descartadas(String zonaId, ZonaEntity zona, long ahora, Map<String, Integer> porMotivo) {
        for (Map.Entry<String, Integer> e : new ArrayList<>(porMotivo.entrySet())) {
            log.warn("Zona {}: {} solicitud(es) de riego descartada(s) al despachar — {}.", zonaId, e.getValue(), e.getKey());
            try {
                historialService.registrarAlerta(zonaId, zona != null ? zona.getName() : zonaId, NAME,
                        new DetalleAlerta(NivelAlerta.WARNING, String.format(
                                "Riego descartado al despachar (%d solicitud%s): %s.",
                                e.getValue(), e.getValue() == 1 ? "" : "es", e.getKey())),
                        ahora);
            } catch (RuntimeException ex) {
                log.error("Zona {}: no se pudo registrar la alerta de riego descartado.", zonaId, ex);
            }
        }
    }

    private boolean hayBloqueoAhora(String zonaId, String sectorId) {
        return !manualLockRepository.findBySectorIdAndActiveTrue(sectorId).isEmpty()
                || !manualLockRepository.findByZonaIdAndActiveTrue(zonaId).isEmpty();
    }

    /**
     * Registra el riego recién abierto en su PROPIA transacción, confirmada acá mismo: un fallo no revierte
     * los riegos de los otros sectores ni aborta el resto del tick.
     */
    private void registrar(SolicitudRiego s, long ahora) {
        try {
            transaccion.executeWithoutResult(estado -> {
                SectorEntity sector = sectorRepository.findById(s.sectorId())
                        .orElseThrow(() -> new IllegalStateException("el sector " + s.sectorId() + " ya no existe"));
                historialService.registrarRiego(sector, s.detalle(), s.regla(), ahora);
            });
        } catch (RuntimeException e) {
            log.error("Sector {}: la válvula se abrió pero no se pudo registrar el riego en el historial.",
                    s.sectorId(), e);
        }
    }

    /** Da por cerrados los riegos cuya duración (más el margen) ya pasó. */
    private void purgar(long ahora) {
        enCurso.values().removeIf(c -> c.finMs() <= ahora);
    }

    private void asegurarReconstruido() {
        if (!reconstruido) {
            reconstruir(reloj.millis());
        }
    }

    /**
     * Reconstruye del historial los riegos que siguen abiertos (primer tick o consulta tras un reinicio).
     *
     * @return {@code false} si la base falló y todavía no se sabe qué hay abierto
     */
    private synchronized boolean reconstruir(long ahora) {
        if (reconstruido) {
            return true;
        }
        try {
            for (HistorialEventoEntity e : historialRepository.riegosDesde(ahora - VENTANA_RECONSTRUCCION_MS)) {
                long fin = e.getTs() + e.getDuracionSeg() * 1000L + MARGEN_MS;
                if (fin > ahora) {
                    enCurso.merge(e.getSectorId(), new Curso(e.getZonaId(), fin),
                            (a, b) -> a.finMs() >= b.finMs() ? a : b);
                }
            }
            reconstruido = true;
            log.info("Despacho de riego: {} riego(s) en curso reconstruidos del historial.", enCurso.size());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Despacho de riego: no se pudo reconstruir lo que está regando ({}). Se reintenta.", ex.getMessage());
            return false;
        }
    }
}
