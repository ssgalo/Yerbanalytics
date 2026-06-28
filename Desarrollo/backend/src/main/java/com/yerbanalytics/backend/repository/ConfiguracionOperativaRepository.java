package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionOperativaRepository extends JpaRepository<ConfiguracionOperativaEntity, Integer> {
}
