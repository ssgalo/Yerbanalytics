package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.repository.DiagnosticoRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * El pasado de los diagnósticos de un sector, para las reglas que miran más que el último (F-F1, F-P1: «el mismo
 * diagnóstico en N capturas seguidas»). Es la única puerta de las reglas bióticas a la tabla de diagnósticos; escribir es
 * de {@code DiagnosticoService}.
 */
@Service
public class DiagnosisHistory {

    private final DiagnosticoRepository repository;

    public DiagnosisHistory(DiagnosticoRepository repository) {
        this.repository = repository;
    }

    /** Las capturas diagnosticadas del sector en los últimos {@code days} días, de la más nueva a la más vieja. */
    public List<ConsecutiveCaptures.Capture> recent(String sectorId, Instant now, int days) {
        long since = now.minus(Duration.ofDays(days)).toEpochMilli();
        return repository.findBySectorIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(sectorId, since).stream()
                .map(d -> new ConsecutiveCaptures.Capture(d.getEstado(), d.getConf(), d.getCreadoEn()))
                .toList();
    }
}
