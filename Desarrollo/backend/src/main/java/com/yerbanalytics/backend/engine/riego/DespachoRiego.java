package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
 *   <li>por zona, descarta lo pendiente con bloqueo manual (del sector o de la zona) o que ya está
 *       regando, y abre los sectores que entren en el cupo libre: publica {@code valve ON
 *       durationSec} y registra el riego en el historial.</li>
 * </ol>
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
    private final ManualLockRepository manualLockRepository;
    private final CatalogoParametrosService parametros;
    private final Clock reloj;

    private final Map<String, Curso> enCurso = new ConcurrentHashMap<>();
    private volatile boolean reconstruido;

    public DespachoRiego(ColaRiego cola,
                         ComandoActuadorPublisher publisher,
                         HistorialService historialService,
                         HistorialRepository historialRepository,
                         SectorRepository sectorRepository,
                         ManualLockRepository manualLockRepository,
                         // Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros)
                         // para armarse, y este necesita el catálogo para leer el valor vigente.
                         @Lazy CatalogoParametrosService parametros,
                         Clock reloj) {
        this.cola = cola;
        this.publisher = publisher;
        this.historialService = historialService;
        this.historialRepository = historialRepository;
        this.sectorRepository = sectorRepository;
        this.manualLockRepository = manualLockRepository;
        this.parametros = parametros;
        this.reloj = reloj;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.SECTORES_SIMULTANEOS);
    }

    /** Lee un parámetro del catálogo, y sólo uno de los que declara: misma garantía que {@code Evaluacion}. */
    double numero(DefinicionParametro p) {
        if (!parametros().contains(p)) {
            throw new ParametroNoDeclaradoException(NAME, p.clave());
        }
        return parametros.vigentes().numero(p);
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

    @Scheduled(fixedDelayString = "${yerbanalytics.riego.despacho-intervalo-ms:10000}")
    @Transactional
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
        long abiertos = enCurso.values().stream().filter(c -> c.zonaId().equals(zonaId)).count();
        long libres = cupo - abiertos;

        for (SolicitudRiego s : cola.pendientes(zonaId)) {
            if (zonaBloqueada || sectoresBloqueados.contains(s.sectorId())) {
                log.info("Sector {}: riego descartado, hay un bloqueo manual activo.", s.sectorId());
                cola.retirar(zonaId, s.sectorId());
                continue;
            }
            if (enCurso.containsKey(s.sectorId())) {
                log.debug("Sector {}: ya está regando, se descarta la solicitud repetida.", s.sectorId());
                cola.retirar(zonaId, s.sectorId());
                continue;
            }
            if (libres <= 0) {
                continue;   // sigue revisando: lo bloqueado se descarta aunque no haya cupo
            }
            SectorEntity sector = sectorRepository.findById(s.sectorId()).orElse(null);
            if (sector == null) {
                log.warn("Sector {}: ya no existe, se descarta su solicitud de riego.", s.sectorId());
                cola.retirar(zonaId, s.sectorId());
                continue;
            }
            DetalleRiego d = s.detalle();
            ComandoActuadorPublisher.Resultado r = publisher.publicar(zonaId, s.sectorId(), "valve", "ON",
                    Map.of("durationSec", d.duracionSeg()));
            if (!r.publicado()) {
                // Sin broker no se registra el riego y la solicitud sigue: el próximo tick reintenta.
                log.warn("Zona {}: no se pudo abrir {} ({}). Se reintenta en el próximo ciclo.",
                        zonaId, s.sectorId(), r.error());
                return;
            }
            // Primero se marca abierto y se saca de la cola; recién después se escribe el historial:
            // si la base falla, la válvula ya abrió y no puede volver a pedirse.
            enCurso.put(s.sectorId(), new Curso(zonaId, ahora + d.duracionSeg() * 1000L + MARGEN_MS));
            cola.retirar(zonaId, s.sectorId());
            libres--;
            try {
                historialService.registrarRiego(sector, d, s.regla(), ahora);
            } catch (RuntimeException e) {
                log.error("Sector {}: la válvula se abrió pero no se pudo registrar el riego en el historial.",
                        s.sectorId(), e);
            }
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
