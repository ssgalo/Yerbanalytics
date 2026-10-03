package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.PreferenciaDashboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreferenciaDashboardRepository extends JpaRepository<PreferenciaDashboardEntity, Long> {
}
