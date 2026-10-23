package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.DiagnosticoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticoRepository extends JpaRepository<DiagnosticoEntity, String> {

    /** Del mas reciente al mas antiguo: es el orden en que los muestra el dashboard. */
    List<DiagnosticoEntity> findAllByOrderByCreadoEnDesc();

    List<DiagnosticoEntity> findBySectorIdOrderByCreadoEnDesc(String sectorId);

    /** El diagnóstico más reciente del sector: es el que P-D1 evalúa. */
    Optional<DiagnosticoEntity> findFirstBySectorIdOrderByCreadoEnDesc(String sectorId);

    /** Los diagnósticos del sector desde {@code since} (epoch ms), del más nuevo al más viejo: las capturas seguidas de F-F1 y F-P1. */
    List<DiagnosticoEntity> findBySectorIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(String sectorId, long since);

    /** El diagnóstico más reciente de una captura: lo usa el seguimiento de la pasada del riel. */
    Optional<DiagnosticoEntity> findFirstByCapturaIdOrderByCreadoEnDesc(String capturaId);
}
