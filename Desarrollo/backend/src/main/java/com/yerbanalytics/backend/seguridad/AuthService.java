package com.yerbanalytics.backend.seguridad;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inicio de sesión y cambio de contraseña propio (HU-01 CA-01/CA-02).
 *
 * <p>El login es resistente a enumeración (design D8): usuario inexistente, contraseña mala,
 * cuenta suspendida o dada de baja producen la misma respuesta. Para que tampoco lo delate el
 * tiempo, un usuario inexistente se compara contra un hash de relleno y el estado se evalúa
 * <em>después</em> de verificar la contraseña.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final SesionService sesiones;
    private final RolPermisoService permisos;
    private final PoliticaSesionService politica;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    /** Hash BCrypt de un valor aleatorio, calculado al arrancar: nadie conoce su contraseña. */
    private final String hashDeRelleno;

    public AuthService(UsuarioRepository usuarios, SesionService sesiones, RolPermisoService permisos,
                       PoliticaSesionService politica, PasswordEncoder encoder, Clock reloj) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.permisos = permisos;
        this.politica = politica;
        this.encoder = encoder;
        this.reloj = reloj;
        this.hashDeRelleno = encoder.encode(UUID.randomUUID().toString());
    }

    public record PerfilSesion(Long id, String username, String nombre, Rol rol, String rolNombre,
                               List<String> permisos, int inactividadMin, boolean debeCambiarClave) {}

    public record LoginExitoso(String sesionId, PerfilSesion perfil) {}

    /** Vacío ante cualquier falla, sin distinguir cuál. */
    @Transactional
    public Optional<LoginExitoso> login(String username, String clave, String ip, String agente) {
        String normalizado = username == null ? "" : username.trim().toLowerCase();
        String claveIngresada = clave == null ? "" : clave;

        Optional<UsuarioEntity> encontrado = normalizado.isEmpty() ? Optional.empty()
                : usuarios.findByUsername(normalizado);
        boolean claveOk = encoder.matches(claveIngresada, encontrado.map(UsuarioEntity::getClaveHash).orElse(hashDeRelleno));
        if (encontrado.isEmpty() || !claveOk || encontrado.get().getEstado() != EstadoUsuario.ACTIVO) {
            return Optional.empty();
        }

        UsuarioEntity u = encontrado.get();
        u.setUltimoIngreso(Instant.now(reloj));
        String sesionId = sesiones.crear(u.getId(), ip, agente);
        return Optional.of(new LoginExitoso(sesionId, perfil(u)));
    }

    @Transactional(readOnly = true)
    public PerfilSesion perfil(Long usuarioId) {
        return perfil(usuarios.findById(usuarioId)
                .orElseThrow(() -> new SeguridadExceptions.NoEncontrado("El usuario no existe.")));
    }

    /**
     * Cambio de contraseña propio. Cierra las demás sesiones del usuario y deja vigente la actual.
     * No se audita: la auditoría de HU-20 CA-03 cubre lo que el Administrador hace sobre otros.
     */
    @Transactional
    public void cambiarClave(UsuarioSesion actual, String claveActual, String nueva) {
        UsuarioEntity u = usuarios.findById(actual.usuarioId())
                .orElseThrow(() -> new SeguridadExceptions.NoEncontrado("El usuario no existe."));
        if (claveActual == null || !encoder.matches(claveActual, u.getClaveHash())) {
            throw new SeguridadExceptions.Invalida("La contraseña actual no es correcta.");
        }
        ReglasClave.validar(u.getUsername(), nueva);
        if (encoder.matches(nueva, u.getClaveHash())) {
            throw new SeguridadExceptions.Invalida("La contraseña nueva tiene que ser distinta de la actual.");
        }
        u.setClaveHash(encoder.encode(nueva));
        u.setDebeCambiarClave(false);
        usuarios.saveAndFlush(u);
        sesiones.revocarDeUsuario(u.getId(), actual.sesionHash());
    }

    private PerfilSesion perfil(UsuarioEntity u) {
        List<String> efectivos = permisos.matriz().stream()
                .filter(rp -> rp.rol() == u.getRol())
                .findFirst()
                .map(RolPermisoService.RolPermisos::permisos)
                .orElse(List.of());
        return new PerfilSesion(u.getId(), u.getUsername(), u.getNombre(), u.getRol(), u.getRol().getNombre(),
                efectivos, politica.inactividadMin(), u.isDebeCambiarClave());
    }
}
