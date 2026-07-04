package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.SensorSimuladoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SensorSimuladoRepository extends JpaRepository<SensorSimuladoEntity, Long> {

    /** Sensores simulados en orden de alta. */
    List<SensorSimuladoEntity> findAllByOrderByIdAsc();

    boolean existsBySerialKey(String serialKey);

    long deleteBySerialKey(String serialKey);
}
