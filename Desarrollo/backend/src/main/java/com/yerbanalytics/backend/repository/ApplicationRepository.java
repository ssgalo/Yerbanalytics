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
}
