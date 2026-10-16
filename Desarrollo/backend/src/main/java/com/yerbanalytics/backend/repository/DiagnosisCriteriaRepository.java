package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.DiagnosisCriteriaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosisCriteriaRepository extends JpaRepository<DiagnosisCriteriaEntity, String> {
}
