package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cierra el estado "Dosificando" de la bomba de un sector. El {@code ActionExecutor} lo pone al ordenar una dosis y
 * no vuelve a ordenar mientras figure así: sin quien lo cierre, el sector quedaba dosificando para siempre y la
 * regla decidía dosificar sin que se publicara nada.
 *
 * <ul>
 *   <li><b>Por ack:</b> el ack final de la bomba (ok, cancelado o error, ver {@link ActuatorStopped}) lo deja
 *       "En espera" al instante. Un {@code reemplazado} no llega: otro comando más nuevo sigue dosificando.</li>
 *   <li><b>Cola del nodo</b> (add-cola-actuadores-nodo): el nodo serializa las cargas y avisa con ACK intermedios
 *       ({@link ActuatorProgress}) cuándo una dosis queda en cola y cuándo arranca. El estado persistido sigue siendo
 *       {@code Dosificando} (es lo que impide que {@code ActionExecutor} ordene otra dosis); la fase vive en memoria,
 *       por sector, y {@link #estadoBomba} muestra "En cola" mientras la dosis espera turno.</li>
 *   <li><b>Por tiempo:</b> si el ack nunca llega (el nodo se reinició a mitad de la dosis, sin WiFi), pasada la
 *       duración de la dosis más {@code yerbanalytics.actuadores.ack-margen-ms} se da por terminada, igual que el
 *       riego. La duración sale de la última fila "Insumo" del sector; el estado vive en la base, así que también
 *       cubre los que quedaron colgados antes de un reinicio del backend.</li>
 * </ul>
 */
@Service
public class SupplyDoseService {

    private static final Logger log = LoggerFactory.getLogger(SupplyDoseService.class);

    public static final String DOSIFICANDO = "Dosificando";
    static final String EN_ESPERA = "En espera";
    static final String EN_COLA = "En cola";

    /** Lo último que el nodo informó de la dosis de un sector: en memoria, se pierde con un reinicio (cae al historial). */
    private record Fase(ActuatorProgress.Phase fase, long atMs, long maxWaitMs) {
    }

    private final Map<String, Fase> fases = new ConcurrentHashMap<>();

    private final SectorRepository sectorRepository;
    private final HistorialRepository historialRepository;
    private final Clock reloj;
    private final long margenMs;

    public SupplyDoseService(SectorRepository sectorRepository,
                             HistorialRepository historialRepository,
                             Clock reloj,
                             @Value("${yerbanalytics.actuadores.ack-margen-ms:90000}") long margenMs) {
        this.sectorRepository = sectorRepository;
        this.historialRepository = historialRepository;
        this.reloj = reloj;
        this.margenMs = margenMs;
    }

    /** El nodo informó cómo va la dosis (en cola / arrancó): se recuerda la fase y la hora de recepción. */
    @EventListener
    public void onActuatorProgress(ActuatorProgress evento) {
        if (!"pump".equals(evento.actuator())) {
            return;
        }
        fases.put(evento.sectorId(), new Fase(evento.phase(), evento.atMs(), evento.maxWaitMs()));
    }

    /**
     * Estado de la bomba para el snapshot: el persistido, salvo que sea {@code Dosificando} y el nodo tenga la dosis
     * esperando turno, que es "En cola". Barato (un mapa en memoria): se pide por sector.
     */
    public String estadoBomba(String sectorId, String persistido) {
        if (DOSIFICANDO.equals(persistido)) {
            Fase f = fases.get(sectorId);
            if (f != null && f.fase() == ActuatorProgress.Phase.QUEUED && !esDeUnaDosisVieja(sectorId, f)) {
                return EN_COLA;
            }
        }
        return persistido;
    }

    /**
     * Un aviso anterior a la última orden de dosis del sector es de una dosis que ya no rige (un {@code reemplazado} no
     * emite {@link ActuatorStopped}, así que su fase quedaría colgada): se descarta.
     */
    private boolean esDeUnaDosisVieja(String sectorId, Fase f) {
        boolean vieja = historialRepository.findFirstBySectorIdAndTipoOrderByTsDesc(sectorId, "Insumo")
                .map(ultima -> f.atMs() < ultima.getTs()).orElse(false);
        if (vieja) {
            fases.remove(sectorId, f);
        }
        return vieja;
    }

    /** El ack final de la bomba cierra la dosis del sector. */
    @EventListener
    public void onActuatorStopped(ActuatorStopped evento) {
        if (!"pump".equals(evento.actuator())) {
            return;
        }
        fases.remove(evento.sectorId());
        if (sectorRepository.cambiarBomba(evento.sectorId(), DOSIFICANDO, EN_ESPERA) > 0) {
            log.info("Sector {}: la dosis terminó (ack {}); la bomba vuelve a En espera.", evento.sectorId(), evento.ackType());
        }
    }

    /** Respaldo por tiempo: cierra las dosis cuyo plazo venció sin ack. */
    @Scheduled(fixedDelayString = "${yerbanalytics.insumo.cierre-revision-ms:15000}")
    public void closeExpired() {
        long ahora = reloj.millis();
        for (SectorEntity s : sectorRepository.findByActuadorPump(DOSIFICANDO)) {
            try {
                if (vencida(s.getId(), ahora)) {
                    fases.remove(s.getId());
                    if (sectorRepository.cambiarBomba(s.getId(), DOSIFICANDO, EN_ESPERA) > 0) {
                        log.warn("Sector {}: la dosis venció sin ack; se da por terminada y la bomba vuelve a En espera.", s.getId());
                    }
                }
            } catch (RuntimeException e) {
                log.error("Sector {}: no se pudo cerrar la dosis vencida; se reintenta en la próxima revisión.", s.getId(), e);
            }
        }
    }

    /** {@code true} si la última dosis del sector ya terminó (o si no hay ninguna: el estado quedó huérfano). */
    private boolean vencida(String sectorId, long ahora) {
        HistorialEventoEntity ultima = historialRepository
                .findFirstBySectorIdAndTipoOrderByTsDesc(sectorId, "Insumo").orElse(null);
        if (ultima == null) {
            return true;
        }
        // Sin duración registrada (una dosis anterior a este cambio) se espera lo más que puede durar una.
        long duracionMs = ultima.getDuracionSeg() != null
                ? ultima.getDuracionSeg() * 1000L
                : ContratoNodo.BOMBA_ML_MAX * ContratoNodo.BOMBA_INSUMO_MS_POR_ML;
        Fase f = fases.get(sectorId);
        if (f != null && f.atMs() >= ultima.getTs()) {   // un aviso anterior a la última orden es de una dosis vieja
            // El nodo informó: STARTED cuenta desde el arranque; QUEUED, desde que se encoló más la espera máxima.
            long desde = f.atMs() + (f.fase() == ActuatorProgress.Phase.QUEUED ? f.maxWaitMs() : 0L);
            return desde + duracionMs + margenMs <= ahora;
        }
        return ultima.getTs() + duracionMs + margenMs <= ahora;
    }
}
