package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.PendingApplicationEntity;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingApplicationRepository extends JpaRepository<PendingApplicationEntity, String> {

    Optional<PendingApplicationEntity> findBySectorIdAndSupply(String sectorId, Supply supply);

    /** Las pendientes de un sector: sirve para saber si hay un fitosanitario esperando (P-D2: va primero). */
    List<PendingApplicationEntity> findBySectorId(String sectorId);

    List<PendingApplicationEntity> findAllByOrderByCreatedTsAsc();
}
