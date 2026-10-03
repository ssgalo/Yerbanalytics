package com.yerbanalytics.backend.engine.parametros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametroReglaRepository extends JpaRepository<ParametroReglaEntity, String> {
}
