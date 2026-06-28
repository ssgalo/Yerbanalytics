package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RustificacionEtapaRepository extends JpaRepository<RustificacionEtapaEntity, Integer> {

    /** Etapas del plan ordenadas por su número de orden. */
    List<RustificacionEtapaEntity> findAllByOrderByOrdenAsc();
}
