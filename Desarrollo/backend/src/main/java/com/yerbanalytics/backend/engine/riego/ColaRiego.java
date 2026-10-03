package com.yerbanalytics.backend.engine.riego;

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

    private final Object candado = new Object();
    private final Map<String, Map<String, SolicitudRiego>> porZona = new HashMap<>();
    private final Set<String> sectoresEnCola = new HashSet<>();

    /** Encola o REEMPLAZA la solicitud del sector. */
    public void solicitar(SolicitudRiego s) {
        synchronized (candado) {
            porZona.computeIfAbsent(s.zonaId(), z -> new LinkedHashMap<>()).put(s.sectorId(), s);
            sectoresEnCola.add(s.sectorId());
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
