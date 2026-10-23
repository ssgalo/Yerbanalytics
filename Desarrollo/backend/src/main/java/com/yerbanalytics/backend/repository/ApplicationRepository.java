package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ApplicationEntity;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String> {

    /** La última aplicación de ese insumo en el sector (P-D2: intervalo mínimo). */
    Optional<ApplicationEntity> findFirstBySectorIdAndSupplyOrderByTsDesc(String sectorId, Supply supply);

    /** La última aplicación de cualquier insumo en el sector (P-D2: una por día). */
    Optional<ApplicationEntity> findFirstBySectorIdOrderByTsDesc(String sectorId);

    /** La última aplicación de ese insumo en cualquier sector de la macro-zona (N-02: «sin fertilizar en 5 días»). */
    Optional<ApplicationEntity> findFirstByZoneIdAndSupplyOrderByTsDesc(String zoneId, Supply supply);

    /** La última aplicación que una regla ordenó en la macro-zona desde {@code since} (cierre de una tanda del plan). */
    Optional<ApplicationEntity> findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
            String zoneId, Supply supply, String ruleName, long since);

    /** Cuántas aplicaciones de ese insumo recibió el sector desde {@code since} (epoch ms): el tope de fitosanitarios y el foco. */
    long countBySectorIdAndSupplyAndTsGreaterThanEqual(String sectorId, Supply supply, long since);
}
