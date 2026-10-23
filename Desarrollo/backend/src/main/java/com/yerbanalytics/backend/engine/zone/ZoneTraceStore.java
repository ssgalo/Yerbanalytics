package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Última traza de las reglas de macro-zona, por zona, en memoria. Igual que la de los sectores es diagnóstico en vivo y no
 * auditoría: no toca la base y no sobrevive a un reinicio. La memoria está acotada por la cantidad de zonas (seis).
 *
 * <p>El Inspector pide la evaluación de un <i>sector</i>; las reglas de zona se le suman a esa traza (ver
 * {@code ReglasEvaluacionesController}), porque la decisión de la zona es la misma para sus cien sectores.
 */
@Component
public class ZoneTraceStore {

    private final ConcurrentHashMap<String, List<TrazaRegla>> byZone = new ConcurrentHashMap<>();

    /** Guarda la traza como la última de la zona (reemplazo atómico: la lista es inmutable). */
    public void save(String zoneId, List<TrazaRegla> trace) {
        byZone.put(zoneId, List.copyOf(trace));
    }

    /** Las reglas de zona de la última evaluación, o vacío si la zona todavía no se evaluó desde el arranque. */
    public Optional<List<TrazaRegla>> latest(String zoneId) {
        return Optional.ofNullable(byZone.get(zoneId));
    }

    /** Descarta todo: al regenerar la topología los ids se reutilizan y una traza vieja sería de otra zona. */
    public void clear() {
        byZone.clear();
    }
}
