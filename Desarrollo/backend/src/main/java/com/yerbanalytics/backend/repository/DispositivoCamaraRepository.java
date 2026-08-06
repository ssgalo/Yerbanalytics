package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.DispositivoCamaraEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispositivoCamaraRepository extends JpaRepository<DispositivoCamaraEntity, String> {

    List<DispositivoCamaraEntity> findByRevocadoFalseOrderByCreadoEnAsc();
}
