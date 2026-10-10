package com.yerbanalytics.backend.seguridad;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    Optional<UsuarioEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UsuarioEntity> findAllByOrderByUsernameAsc();

    List<UsuarioEntity> findByEstadoNotOrderByUsernameAsc(EstadoUsuario estado);

    boolean existsByRolAndEstado(Rol rol, EstadoUsuario estado);

    /**
     * Administradores activos con bloqueo de escritura. Se toma antes de suspender, dar de baja o
     * cambiar de rol a un Administrador: dos operaciones concurrentes sobre administradores
     * distintos se serializan acá y la segunda ve que quedaría uno solo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u where u.rol = com.yerbanalytics.backend.seguridad.Rol.ADMINISTRADOR "
            + "and u.estado = com.yerbanalytics.backend.seguridad.EstadoUsuario.ACTIVO")
    List<UsuarioEntity> bloquearAdministradoresActivos();
}
