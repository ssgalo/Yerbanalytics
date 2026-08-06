package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.DiagnosticoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticoRepository extends JpaRepository<DiagnosticoEntity, String> {

    /** Del mas reciente al mas antiguo: es el orden en que los muestra el dashboard. */
    List<DiagnosticoEntity> findAllByOrderByCreadoEnDesc();

    List<DiagnosticoEntity> findBySectorIdOrderByCreadoEnDesc(String sectorId);
}
