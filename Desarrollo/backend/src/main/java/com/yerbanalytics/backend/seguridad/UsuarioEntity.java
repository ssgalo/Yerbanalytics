package com.yerbanalytics.backend.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persona o cuenta de servicio que usa la API de plataforma.
 *
 * <p>El {@code username} se guarda en minúsculas y es único entre <em>todos</em> los usuarios,
 * bajas incluidas: así la auditoría nunca queda con dos personas distintas bajo el mismo nombre.
 * La contraseña sólo existe como hash BCrypt.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 40, updatable = false)
    private String username;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 30)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoUsuario estado;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    /** Contraseña temporal asignada por el Administrador: hay que cambiarla antes de operar. */
    @Column(name = "debe_cambiar_clave", nullable = false)
    private boolean debeCambiarClave;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_ingreso")
    private Instant ultimoIngreso;

    /** Bloqueo optimista: dos administradores editando al mismo usuario no se pisan en silencio. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
