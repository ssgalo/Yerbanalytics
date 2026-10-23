package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.PlanRustificacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRustificacionRepository extends JpaRepository<PlanRustificacionEntity, String> {

    /** El plan vigente de la zona (el que no se eliminó), si tiene. */
    Optional<PlanRustificacionEntity> findFirstByZonaIdAndCanceladoEnIsNull(String zonaId);

    /** Los planes vigentes de todas las zonas (el horario de la mediasombra los lee una vez por revisión). */
    List<PlanRustificacionEntity> findByCanceladoEnIsNull();
}
