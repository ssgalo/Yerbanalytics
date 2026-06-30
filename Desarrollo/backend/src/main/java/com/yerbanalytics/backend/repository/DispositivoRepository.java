package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.DispositivoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoRepository extends JpaRepository<DispositivoEntity, String> {

    Optional<DispositivoEntity> findBySerial(String serial);

    List<DispositivoEntity> findByZonaIdAndTipo(String zonaId, String tipo);

    List<DispositivoEntity> findBySectorIdAndTipo(String sectorId, String tipo);
}
