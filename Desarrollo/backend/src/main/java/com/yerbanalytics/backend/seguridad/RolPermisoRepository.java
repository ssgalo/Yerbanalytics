package com.yerbanalytics.backend.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolPermisoRepository extends JpaRepository<RolPermisoEntity, RolPermisoEntity.Clave> {

    /** Limpia el contexto de persistencia: si no, reinsertar la misma clave chocaría con la copia borrada. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RolPermisoEntity rp where rp.rol = :rol")
    void borrarDeRol(@Param("rol") Rol rol);
}
