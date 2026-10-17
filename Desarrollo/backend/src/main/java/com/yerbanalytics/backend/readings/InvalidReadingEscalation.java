package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.InvalidReadingEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.InvalidReadingRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * S-03, la alerta: la primera vez que una métrica se descarta en un ciclo de lectura es un {@code WARNING}; si la
 * misma métrica se vuelve a descartar en el ciclo siguiente es un {@code CRITICAL} (sensor con falla), y sigue siéndolo
 * mientras la racha continúe. Una lectura válida reinicia la cuenta.
 *
 * <p>La racha se persiste ({@link InvalidReadingEntity}) para que un reinicio en medio no la pierda. Descartar dos
 * veces en el mismo ciclo (p. ej. una lectura manual además del pulso) cuenta como una: la racha se mide en ciclos,
 * no en lecturas.
 */
@Service
class InvalidReadingEscalation {

    private final InvalidReadingRepository repository;
    private final HistorialService historial;
    private final Clock reloj;

    InvalidReadingEscalation(InvalidReadingRepository repository, HistorialService historial, Clock relojVivero) {
        this.repository = repository;
        this.historial = historial;
        this.reloj = relojVivero;
    }

    /**
     * {@code metricKey} se descartó en el ciclo que empieza en {@code cycle}; {@code previousCycle} es el inmediato
     * anterior. Suma uno a la racha si el ciclo anterior también falló (si no, vuelve a uno) y deja la alerta.
     */
    void registerDiscard(ZonaEntity zona, String metricKey, String metricLabel, Instant cycle, Instant previousCycle) {
        Optional<InvalidReadingEntity> row = repository.findById(InvalidReadingEntity.idOf(zona.getId(), metricKey));
        if (row.isPresent() && row.get().getCycleStart() == cycle.toEpochMilli()) {
            return;   // ya contó en este ciclo
        }
        boolean consecutive = row.isPresent() && row.get().getCycleStart() == previousCycle.toEpochMilli();
        int streak = consecutive ? row.get().getStreak() + 1 : 1;
        repository.save(new InvalidReadingEntity(zona.getId(), metricKey, cycle.toEpochMilli(), streak));

        DetalleAlerta alert = streak == 1
                ? new DetalleAlerta(NivelAlerta.WARNING, "Lectura inválida en " + metricLabel
                        + ": el valor llegó fuera del rango físico posible y se descartó en este ciclo.")
                : new DetalleAlerta(NivelAlerta.CRITICAL, "Sensor de " + metricLabel + " con falla: revisar. "
                        + "Lleva " + streak + " ciclos de lectura seguidos con valores fuera del rango físico.");
        historial.registrarAlerta(zona.getId(), zona.getName(), ReadingValidationGuard.NAME, alert, reloj.millis());
    }

    /** Estas métricas llegaron válidas: su racha, si había, se reinicia. Sin métricas no toca la base. */
    void registerValid(String zoneId, Collection<String> metricKeys) {
        if (!metricKeys.isEmpty()) {
            repository.deleteByZoneIdAndMetricIn(zoneId, metricKeys);
        }
    }
}
