package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.ActuatorShutdown;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * El único camino por el que el sistema le ordena una posición a la mediasombra de una macro-zona: lo usan las reglas de
 * zona (por el {@code ZoneActionExecutor}) y el horario de la mediasombra ({@link HorarioMediasombraService}). Publica el
 * {@code shade SET} por el comando de zona, guarda la última posición ordenada en la zona y deja el historial.
 *
 * <p>No decide si se puede: las guardas (bloqueo, hardware, falla) son de quien llama. El estado seguro
 * ({@link ActuatorShutdown}) y las secuencias de Demo Expo publican por su cuenta (necesitan su propio commandId o su
 * prioridad), pero la posición la registran acá con {@link #registrarPosicion}.
 */
@Service
public class MediasombraService {

    private static final Logger log = LoggerFactory.getLogger(MediasombraService.class);

    /** Apertura del {@code shade SET}: 100 enrolla (abierta), 0 despliega (cerrada). El hardware es binario. */
    public static final int APERTURA_ABIERTA = 100;
    public static final int APERTURA_CERRADA = 0;

    private final ComandoActuadorPublisher publisher;
    private final ZonaRepository zonaRepository;
    private final HistorialService historial;
    private final Clock reloj;

    public MediasombraService(ComandoActuadorPublisher publisher, ZonaRepository zonaRepository,
                              HistorialService historial, Clock relojVivero) {
        this.publisher = publisher;
        this.zonaRepository = zonaRepository;
        this.historial = historial;
        this.reloj = relojVivero;
    }

    /** Apertura del {@code shade SET} para una posición. */
    public static int apertura(boolean abrir) {
        return abrir ? APERTURA_ABIERTA : APERTURA_CERRADA;
    }

    /** "abierta" / "cerrada". */
    public static String nombre(boolean abierta) {
        return abierta ? "abierta" : "cerrada";
    }

    /**
     * Ordena abrir o cerrar la malla de la zona. Si se publicó, la zona queda con esa posición; si no, no se toca (no se
     * sabe dónde quedó) y el historial lo dice.
     *
     * @param regla  quién la mueve (nombre de la regla o del servicio), para el historial
     * @param motivo por qué, legible para el agrónomo
     * @return {@code true} si el comando salió al broker
     */
    @Transactional
    public boolean mover(ZonaEntity zona, boolean abrir, String regla, String motivo) {
        ComandoActuadorPublisher.Resultado r = publisher.publicarZona(zona.getId(), "shade", "SET",
                Map.of("targetPct", apertura(abrir)));
        if (r.publicado()) {
            zona.setMediasombraAbierta(abrir);
            zonaRepository.save(zona);
            log.info("Zona {}: mediasombra {} ({}) — {}", zona.getId(), nombre(abrir), regla, motivo);
        } else {
            log.warn("Zona {}: la orden de mediasombra {} no se publicó — {}", zona.getId(), nombre(abrir), r.error());
        }
        historial.registrarMediasombra(zona.getId(), zona.getName(), regla, motivo,
                r.publicado() ? "Mediasombra " + nombre(abrir) + "." : "No se pudo enviar la orden: " + r.error(),
                r.publicado() ? "Orden enviada al nodo de la macro-zona." : "La mediasombra no recibió la orden.",
                r.publicado() ? "Efectiva" : "Fallida", reloj.millis());
        return r.publicado();
    }

    /** Anota la posición que otro ordenó a la malla (estado seguro, secuencias de Demo Expo). Sin historial. */
    @Transactional
    public void registrarPosicion(String zonaId, boolean abierta) {
        zonaRepository.findById(zonaId).ifPresent(z -> {
            z.setMediasombraAbierta(abierta);
            zonaRepository.save(z);
        });
    }
}
