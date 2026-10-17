package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.InvalidReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/** Racha de ciclos con lectura inválida por (zona, métrica), la base del escalamiento de S-03. */
@Repository
public interface InvalidReadingRepository extends JpaRepository<InvalidReadingEntity, String> {

    /** Borra, en una sola sentencia, la racha de esas métricas de la zona (una lectura válida la reinicia). */
    @Transactional
    @Modifying
    @Query("delete from InvalidReadingEntity e where e.zoneId = :zoneId and e.metric in :metrics")
    void deleteByZoneIdAndMetricIn(@Param("zoneId") String zoneId, @Param("metrics") Collection<String> metrics);
}
