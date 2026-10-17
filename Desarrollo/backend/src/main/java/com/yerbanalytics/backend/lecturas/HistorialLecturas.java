package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.model.LecturaZonaEntity;
import com.yerbanalytics.backend.repository.LecturaZonaRepository;
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
 * N lecturas y el máximo, el mínimo, la media y el conteo de una métrica en una {@link VentanaLecturas}.
 *
 * <p><b>Sin dato no es cero.</b> Sin lecturas con esa métrica en la ventana, los agregados devuelven vacío y el
 * conteo 0: la regla decide si es {@code SIN_DATO}. Una lectura en la que la zona nunca reportó la métrica no cuenta.
 * Con menos lecturas de las que la regla exige, el conteo le permite registrar "sin dato" en vez de decidir con poco.
 *
 * <p>Cada consulta trae sólo la ventana pedida de una zona (acotada por la retención), nunca la tabla entera. Escribir
 * es de {@link RegistroLecturas}.
 */
@Service
public class HistorialLecturas {

    private final LecturaZonaRepository repository;

    public HistorialLecturas(LecturaZonaRepository repository) {
        this.repository = repository;
    }

    /**
     * Las últimas {@code n} lecturas de la zona, de la más vieja a la más nueva.
     *
     * @param soloPulso {@code true} = sólo las que respondieron a un pulso
     */
    public List<LecturaZonaEntity> ultimas(String zonaId, int n, boolean soloPulso) {
        if (n <= 0) {
            return List.of();
        }
        PageRequest pagina = PageRequest.of(0, n);
        List<LecturaZonaEntity> masNuevasPrimero = soloPulso
                ? repository.findByZonaIdAndOrigenOrderByTsDesc(zonaId, OrigenLectura.PULSO, pagina)
                : repository.findByZonaIdOrderByTsDesc(zonaId, pagina);
        List<LecturaZonaEntity> cronologicas = new ArrayList<>(masNuevasPrimero);
        Collections.reverse(cronologicas);
        return cronologicas;
    }

    public Optional<Double> maximo(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        return estadisticas(zonaId, metrica, ventana).map(DoubleSummaryStatistics::getMax);
    }

    public Optional<Double> minimo(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        return estadisticas(zonaId, metrica, ventana).map(DoubleSummaryStatistics::getMin);
    }

    public Optional<Double> media(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        return estadisticas(zonaId, metrica, ventana).map(DoubleSummaryStatistics::getAverage);
    }

    /** Cuántas lecturas de la ventana traen esa métrica. */
    public int conteo(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        return (int) valores(zonaId, metrica, ventana).count();
    }

    private Optional<DoubleSummaryStatistics> estadisticas(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        DoubleSummaryStatistics estadisticas = valores(zonaId, metrica, ventana).summaryStatistics();
        return estadisticas.getCount() == 0 ? Optional.empty() : Optional.of(estadisticas);
    }

    private DoubleStream valores(String zonaId, MetricaLectura metrica, VentanaLecturas ventana) {
        long desde = ventana.desde().toEpochMilli();
        long hasta = ventana.hasta() == null ? Long.MAX_VALUE : ventana.hasta().toEpochMilli();
        return repository.findByZonaIdAndTsBetweenOrderByTsAsc(zonaId, desde, hasta).stream()
                .filter(l -> !ventana.exigePulso() || l.getOrigen() == OrigenLectura.PULSO)
                .filter(l -> enFranja(ventana, l))
                .map(metrica::valorEn)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue);
    }

    private static boolean enFranja(VentanaLecturas ventana, LecturaZonaEntity lectura) {
        if (ventana.franja() == null) {
            return true;
        }
        return ventana.franja().contieneHastaElMinuto(
                Instant.ofEpochMilli(lectura.getTs()).atZone(ZonaHorariaVivero.ZONA).toLocalTime());
    }
}
