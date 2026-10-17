package com.yerbanalytics.backend.repository;

import com.yerbanalytics.backend.model.SectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<SectorEntity, String> {
    List<SectorEntity> findByZonaId(String zonaId);

    /** Los sectores cuya bomba está en el estado dado (p. ej. {@code Dosificando}). */
    List<SectorEntity> findByActuadorPump(String actuadorPump);

    /**
     * Cambia sólo el estado de la bomba, y sólo si sigue en {@code desde}: un UPDATE dirigido, para no pisar el resto
     * de la fila con una copia vieja del sector. Devuelve las filas cambiadas (0 o 1).
     */
    @Transactional
    @Modifying
    @Query("UPDATE SectorEntity s SET s.actuadorPump = :hacia WHERE s.id = :id AND s.actuadorPump = :desde")
    int cambiarBomba(@Param("id") String id, @Param("desde") String desde, @Param("hacia") String hacia);
}
