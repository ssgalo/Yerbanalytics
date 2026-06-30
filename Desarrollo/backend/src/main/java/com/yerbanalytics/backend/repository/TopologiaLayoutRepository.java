package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.TopologiaLayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopologiaLayoutRepository extends JpaRepository<TopologiaLayoutEntity, Integer> {
}
