package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.OrdenCapturaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenCapturaRepository extends JpaRepository<OrdenCapturaEntity, String> {

    /** Pendientes a drenar cuando un dispositivo abre su canal, en orden de creacion. */
    List<OrdenCapturaEntity> findByEstadoOrderByCreadaEnAsc(OrdenCapturaEntity.Estado estado);

    /** Barrido del watchdog: entregadas cuyo plazo ya paso. */
    List<OrdenCapturaEntity> findByEstadoAndVenceEnLessThan(OrdenCapturaEntity.Estado estado, Long ahora);

    List<OrdenCapturaEntity> findTop50ByOrderByCreadaEnDesc();
}
