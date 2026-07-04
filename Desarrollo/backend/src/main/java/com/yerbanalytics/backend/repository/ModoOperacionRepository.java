package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ModoOperacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModoOperacionRepository extends JpaRepository<ModoOperacionEntity, Integer> {
}
