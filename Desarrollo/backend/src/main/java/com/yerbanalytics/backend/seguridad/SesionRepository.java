package com.yerbanalytics.backend.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SesionRepository extends JpaRepository<SesionEntity, String> {

    /** Cierra las sesiones abiertas de un usuario, salvo {@code exceptoHash} (puede ser null). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SesionEntity s set s.cerradaEn = :ahora, s.motivoCierre = :motivo "
            + "where s.usuarioId = :usuarioId and s.cerradaEn is null "
            + "and (:exceptoHash is null or s.idHash <> :exceptoHash)")
    int cerrarDeUsuario(@Param("usuarioId") Long usuarioId, @Param("exceptoHash") String exceptoHash,
                        @Param("motivo") MotivoCierre motivo, @Param("ahora") Instant ahora);

    /** Cierra las sesiones abiertas de todos los usuarios con un rol, salvo {@code exceptoHash}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SesionEntity s set s.cerradaEn = :ahora, s.motivoCierre = :motivo "
            + "where s.cerradaEn is null and (:exceptoHash is null or s.idHash <> :exceptoHash) "
            + "and s.usuarioId in (select u.id from UsuarioEntity u where u.rol = :rol)")
    int cerrarDeRol(@Param("rol") Rol rol, @Param("exceptoHash") String exceptoHash,
                    @Param("motivo") MotivoCierre motivo, @Param("ahora") Instant ahora);

    /** Barrido: borra lo cerrado o inactivo desde antes de {@code limite}. */
    @Modifying
    @Query("delete from SesionEntity s where (s.cerradaEn is not null and s.cerradaEn < :limite) "
            + "or s.ultimaActividad < :limite")
    int borrarAnterioresA(@Param("limite") Instant limite);
}
