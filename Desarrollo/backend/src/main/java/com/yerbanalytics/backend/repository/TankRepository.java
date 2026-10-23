package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.TankEntity;
import com.yerbanalytics.backend.supply.Supply;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TankRepository extends JpaRepository<TankEntity, Long> {

    List<TankEntity> findAllByOrderByZoneIdAscSupplyAsc();

    Optional<TankEntity> findByZoneIdAndSupply(String zoneId, Supply supply);

    /**
     * Lo mismo con bloqueo de escritura: un descuento y una carga (o dos descuentos de hilos distintos: la telemetría y el
     * reintento de las diferidas) no pueden pisarse el nivel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TankEntity t WHERE t.zoneId = :zoneId AND t.supply = :supply")
    Optional<TankEntity> lockByZoneIdAndSupply(@Param("zoneId") String zoneId, @Param("supply") Supply supply);
}
