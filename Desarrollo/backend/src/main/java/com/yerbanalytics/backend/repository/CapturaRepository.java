package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.CapturaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CapturaRepository extends JpaRepository<CapturaEntity, String> {

    /** Una orden produce a lo sumo una captura: sostiene la idempotencia del 409. */
    Optional<CapturaEntity> findByOrdenId(String ordenId);

    List<CapturaEntity> findBySectorIdOrderByRecibidaEnDesc(String sectorId);
}
