package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.UmbralMetricaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UmbralMetricaRepository extends JpaRepository<UmbralMetricaEntity, String> {
}
