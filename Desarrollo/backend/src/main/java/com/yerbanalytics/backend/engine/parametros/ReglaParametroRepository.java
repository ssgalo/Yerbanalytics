package com.yerbanalytics.backend.engine.parametros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReglaParametroRepository extends JpaRepository<ReglaParametroEntity, ReglaParametroId> {
}
