package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;
import java.util.stream.DoubleStream;

/**
 * Las consultas del pasado de las lecturas, únicas para todas las reglas (N-02, F-C1, F-F*, F-P1, F-S*…): las últimas
 * N lecturas y el máximo, el mínimo, la media y el conteo de una métrica en una {@link ReadingWindow}.
 *
 * <p><b>Sin dato no es cero.</b> Sin lecturas con esa métrica en la ventana, los agregados devuelven vacío y el
 * conteo 0: la regla decide si es {@code SIN_DATO}. Una lectura en la que la zona nunca reportó la métrica no cuenta.
 * Con menos lecturas de las que la regla exige, el conteo le permite registrar "sin dato" en vez de decidir con poco.
 *
 * <p>Cada consulta trae sólo la ventana pedida de una zona (acotada por la retención), nunca la tabla entera. Escribir
 * es de {@link ReadingRecorder}.
 */
@Service
public class ReadingHistory {

    private final ZoneReadingRepository repository;

    public ReadingHistory(ZoneReadingRepository repository) {
        this.repository = repository;
    }

    /**
     * Las últimas {@code n} lecturas de la zona, de la más vieja a la más nueva.
     *
     * @param onlyPulse {@code true} = sólo las que respondieron a un pulso
     */
    public List<ZoneReadingEntity> latest(String zoneId, int n, boolean onlyPulse) {
        if (n <= 0) {
            return List.of();
        }
        PageRequest page = PageRequest.of(0, n);
        List<ZoneReadingEntity> newestFirst = onlyPulse
                ? repository.findByZoneIdAndOriginOrderByTsDesc(zoneId, ReadingOrigin.PULSE, page)
                : repository.findByZoneIdOrderByTsDesc(zoneId, page);
        List<ZoneReadingEntity> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    public Optional<Double> max(String zoneId, ReadingMetric metric, ReadingWindow window) {
        return statistics(zoneId, metric, window).map(DoubleSummaryStatistics::getMax);
    }

    public Optional<Double> min(String zoneId, ReadingMetric metric, ReadingWindow window) {
        return statistics(zoneId, metric, window).map(DoubleSummaryStatistics::getMin);
    }

    public Optional<Double> average(String zoneId, ReadingMetric metric, ReadingWindow window) {
        return statistics(zoneId, metric, window).map(DoubleSummaryStatistics::getAverage);
    }

    /** Cuántas lecturas de la ventana traen esa métrica. */
    public int count(String zoneId, ReadingMetric metric, ReadingWindow window) {
        return (int) values(zoneId, metric, window).count();
    }

    private Optional<DoubleSummaryStatistics> statistics(String zoneId, ReadingMetric metric, ReadingWindow window) {
        DoubleSummaryStatistics statistics = values(zoneId, metric, window).summaryStatistics();
        return statistics.getCount() == 0 ? Optional.empty() : Optional.of(statistics);
    }

    private DoubleStream values(String zoneId, ReadingMetric metric, ReadingWindow window) {
        long from = window.from().toEpochMilli();
        long to = window.to() == null ? Long.MAX_VALUE : window.to().toEpochMilli();
        return repository.findByZoneIdAndTsBetweenOrderByTsAsc(zoneId, from, to).stream()
                .filter(r -> !window.requirePulse() || r.getOrigin() == ReadingOrigin.PULSE)
                .filter(r -> inBand(window, r))
                .map(metric::valueIn)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue);
    }

    private static boolean inBand(ReadingWindow window, ZoneReadingEntity reading) {
        if (window.band() == null) {
            return true;
        }
        return window.band().contieneHastaElMinuto(
                Instant.ofEpochMilli(reading.getTs()).atZone(ZonaHorariaVivero.ZONA).toLocalTime());
    }
}
