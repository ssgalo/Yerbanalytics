package com.yerbanalytics.backend.engine.overrides;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleStateRepository extends JpaRepository<RuleStateEntity, String> {
}
