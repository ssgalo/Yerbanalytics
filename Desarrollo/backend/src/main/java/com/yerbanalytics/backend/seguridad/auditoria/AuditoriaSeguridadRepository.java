package com.yerbanalytics.backend.seguridad.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sólo altas y consultas. Extiende {@link Repository} y no {@code JpaRepository} a propósito: así
 * no existen {@code delete} ni {@code saveAll} sobre registros ya escritos, ni siquiera por error.
 */
public interface AuditoriaSeguridadRepository extends Repository<AuditoriaSeguridadEntity, Long> {

    AuditoriaSeguridadEntity save(AuditoriaSeguridadEntity registro);

    Optional<AuditoriaSeguridadEntity> findTopByOrderByIdDesc();

    List<AuditoriaSeguridadEntity> findAllByOrderByIdAsc();

    long count();

    /**
     * Filtros opcionales: un {@code autor}, {@code objetivo} o {@code tipo} nulo no filtra
     * ({@code autor} y {@code objetivo} van en minúsculas). El rango de fechas es siempre
     * obligatorio: quien no filtra por fecha pasa los extremos.
     */
    @Query("select a from AuditoriaSeguridadEntity a "
            + "where (:autor is null or lower(a.autorUsername) = :autor) "
            + "and (:objetivo is null or lower(a.objetivoRef) = :objetivo) "
            + "and (:tipo is null or a.tipo = :tipo) "
            + "and a.ocurridoEn >= :desde and a.ocurridoEn <= :hasta "
            + "order by a.id desc")
    Page<AuditoriaSeguridadEntity> buscar(@Param("autor") String autor,
                                          @Param("objetivo") String objetivo,
                                          @Param("tipo") TipoAuditoria tipo,
                                          @Param("desde") Instant desde,
                                          @Param("hasta") Instant hasta,
                                          Pageable pagina);
}
