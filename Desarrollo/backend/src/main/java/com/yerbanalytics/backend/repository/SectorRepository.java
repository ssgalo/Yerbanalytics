package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.SectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<SectorEntity, String> {
    List<SectorEntity> findByZonaId(String zonaId);
}
