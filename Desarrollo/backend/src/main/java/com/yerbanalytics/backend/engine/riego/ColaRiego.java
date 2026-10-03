package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cola de riego por macro-zona, en memoria: {@code zonaId → (sectorId → solicitud)}.
 *
 * <p>La tocan tres actores a la vez: el hilo MQTT (telemetría: pide o retira), el scheduler del
 * despacho (lee y saca) y los controllers del snapshot (preguntan si un sector está en cola). Con
 * a lo sumo seiscientas entradas y operaciones de microsegundos, un único candado es lo más simple
 * y no tiene ventanas de carrera entre el mapa por zona y el índice por sector.
 *
 * <p>La última decisión de la telemetría manda: pedir de nuevo reemplaza la solicitud del sector
 * (otro volumen), y retirar la cancela (R-04, bloqueo manual, la ventana cerró).
 */
@Component
public class ColaRiego {

    private static final Logger log = LoggerFactory.getLogger(ColaRiego.class);

    private final Object candado = new Object();
    private final Map<String, Map<String, SolicitudRiego>> porZona = new HashMap<>();
    private final Set<String> sectoresEnCola = new HashSet<>();

    /**
     * Encola o REEMPLAZA la solicitud del sector. Sólo entra una orden despachable: volumen finito y
     * positivo, y duración entre 1 s y el máximo del contrato de la válvula. Una orden vacía (0 L / 0 s,
     * que sale de un déficit que redondea a cero) o fuera del contrato se rechaza, y como la última decisión
     * manda, cancela también la solicitud anterior del sector.
     *
     * @return {@code true} si quedó en la cola; {@code false} si se rechazó
     */
    public boolean solicitar(SolicitudRiego s) {
        if (!esDespachable(s)) {
            log.warn("Sector {}: solicitud de riego rechazada por no ser despachable ({}).", s.sectorId(), s.detalle());
            retirar(s.zonaId(), s.sectorId());
            return false;
        }
        synchronized (candado) {
            porZona.computeIfAbsent(s.zonaId(), z -> new LinkedHashMap<>()).put(s.sectorId(), s);
            sectoresEnCola.add(s.sectorId());
        }
        return true;
    }

    private static boolean esDespachable(SolicitudRiego s) {
        DetalleRiego d = s.detalle();
        return d != null
                && Double.isFinite(d.volumenL()) && d.volumenL() > 0
                && d.duracionSeg() >= 1 && d.duracionSeg() <= ContratoNodo.DURACION_VALVULA_MAX_SEG;
    }

    /** {@code true} si {@code s} (la MISMA instancia, no una igual por valor) sigue siendo la solicitud del sector. */
    public boolean esVigente(SolicitudRiego s) {
        synchronized (candado) {
            Map<String, SolicitudRiego> zona = porZona.get(s.zonaId());
            return zona != null && zona.get(s.sectorId()) == s;
        }
    }

    /**
     * Reclama {@code s} para despacharla: la saca de la cola sólo si SIGUE siendo la solicitud vigente del
     * sector (misma instancia). Si la telemetría la retiró (R-04, bloqueo) o la reemplazó mientras el despacho
     * trabajaba sobre su copia, devuelve {@code false} y no se publica nada; así una cancelación posterior a la
     * copia no se pierde y una solicitud nueva nunca se borra por error.
     */
    public boolean retirarSiCoincide(SolicitudRiego s) {
        synchronized (candado) {
            if (!esVigente(s)) {
                return false;
            }
            retirar(s.zonaId(), s.sectorId());
            return true;
        }
    }

    /** Devuelve a la cola una solicitud reclamada que no se pudo despachar, salvo que ya haya otra más nueva. */
    public void reponerSiAusente(SolicitudRiego s) {
        synchronized (candado) {
            Map<String, SolicitudRiego> zona = porZona.computeIfAbsent(s.zonaId(), z -> new LinkedHashMap<>());
            if (zona.putIfAbsent(s.sectorId(), s) == null) {
                sectoresEnCola.add(s.sectorId());
            }
        }
    }

    /** Vacía la cola (al regenerar la topología los sectores pendientes pueden no existir más). */
    public void limpiar() {
        synchronized (candado) {
            porZona.clear();
            sectoresEnCola.clear();
        }
    }

    /** Saca la solicitud del sector, si está. No hace nada si no. */
    public void retirar(String zonaId, String sectorId) {
        synchronized (candado) {
            Map<String, SolicitudRiego> zona = porZona.get(zonaId);
            if (zona == null) {
                return;
            }
            zona.remove(sectorId);
            sectoresEnCola.remove(sectorId);
            if (zona.isEmpty()) {
                porZona.remove(zonaId);
            }
        }
    }

    /** Copia de lo pendiente de la zona, en orden de numeración de sector. */
    public List<SolicitudRiego> pendientes(String zonaId) {
        synchronized (candado) {
            Map<String, SolicitudRiego> zona = porZona.get(zonaId);
            if (zona == null) {
                return List.of();
            }
            List<SolicitudRiego> copia = new ArrayList<>(zona.values());
            copia.sort(Comparator.comparingInt(SolicitudRiego::numero).thenComparing(SolicitudRiego::sectorId));
            return copia;
        }
    }

    /** {@code true} si el sector tiene una solicitud pendiente. */
    public boolean contiene(String sectorId) {
        synchronized (candado) {
            return sectoresEnCola.contains(sectorId);
        }
    }

    /** Zonas con al menos una solicitud pendiente. */
    public Set<String> zonas() {
        synchronized (candado) {
            return new HashSet<>(porZona.keySet());
        }
    }
}
