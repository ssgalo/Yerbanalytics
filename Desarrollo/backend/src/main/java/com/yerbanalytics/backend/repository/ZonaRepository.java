package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.ZonaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZonaRepository extends JpaRepository<ZonaEntity, String> {

    @Query("SELECT DISTINCT z FROM ZonaEntity z LEFT JOIN FETCH z.sectors s ORDER BY z.id ASC")
    List<ZonaEntity> findAllWithSectors();
}
