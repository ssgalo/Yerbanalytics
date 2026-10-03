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

    /** El diagnóstico más reciente de una captura: lo usa el seguimiento de la pasada del riel. */
    Optional<DiagnosticoEntity> findFirstByCapturaIdOrderByCreadoEnDesc(String capturaId);
}
