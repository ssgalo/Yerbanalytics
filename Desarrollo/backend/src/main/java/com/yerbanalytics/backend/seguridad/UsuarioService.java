package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import com.yerbanalytics.backend.seguridad.auditoria.ObjetivoAuditoria;
import com.yerbanalytics.backend.seguridad.auditoria.TipoAuditoria;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Gestión de usuarios por el Administrador (HU-20 CA-01/CA-02).
 *
 * <p>Cada operación se audita en su misma transacción y, si cambia lo que el usuario puede hacer
 * (rol, estado, contraseña), revoca sus sesiones en el acto: la siguiente petición de ese usuario
 * recibe {@code 401 SESION_REVOCADA}. La revocación va siempre al final de la operación, porque
 * vacía el contexto de persistencia.
 */
@Service
public class UsuarioService {

    private static final Pattern FORMATO_USERNAME = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final int NOMBRE_MAX = 120;

    private final UsuarioRepository usuarios;
    private final SesionService sesiones;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    public UsuarioService(UsuarioRepository usuarios, SesionService sesiones, AuditoriaService auditoria,
                          PasswordEncoder encoder, Clock reloj) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    /** Lo que ve la API. Nunca lleva la contraseña ni su hash. */
    public record UsuarioDto(Long id, String username, String nombre, Rol rol, EstadoUsuario estado,
                             boolean debeCambiarClave, Instant creadoEn, Instant ultimoIngreso) {
        static UsuarioDto de(UsuarioEntity u) {
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNombre(), u.getRol(), u.getEstado(),
                    u.isDebeCambiarClave(), u.getCreadoEn(), u.getUltimoIngreso());
        }
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listar(boolean incluirBajas) {
        List<UsuarioEntity> lista = incluirBajas
                ? usuarios.findAllByOrderByUsernameAsc()
                : usuarios.findByEstadoNotOrderByUsernameAsc(EstadoUsuario.BAJA);
        return lista.stream().map(UsuarioDto::de).toList();
    }

    @Transactional
    public UsuarioDto alta(String username, String nombre, String rol, String clave) {
        String normalizado = username == null ? "" : username.trim().toLowerCase();
        if (!FORMATO_USERNAME.matcher(normalizado).matches()) {
            throw new SeguridadExceptions.Invalida(
                    "El nombre de usuario debe tener entre 3 y 40 caracteres: letras, números, '.', '_' o '-'.");
        }
        String nombreValido = validarNombre(nombre);
        Rol rolValido = parsearRol(rol);
        ReglasClave.validar(normalizado, clave);
        if (usuarios.existsByUsername(normalizado)) {
            throw new SeguridadExceptions.Conflicto("Ya existe un usuario '" + normalizado
                    + "' (o existió y fue dado de baja): los nombres de usuario no se reutilizan.");
        }

        UsuarioEntity u = new UsuarioEntity();
        u.setUsername(normalizado);
        u.setNombre(nombreValido);
        u.setRol(rolValido);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setClaveHash(encoder.encode(clave));
        u.setDebeCambiarClave(true);
        u.setCreadoEn(Instant.now(reloj));
        usuarios.saveAndFlush(u);

        auditoria.registrar(TipoAuditoria.USUARIO_ALTA, ObjetivoAuditoria.USUARIO, u.getUsername(),
                Map.of("nuevo", Map.of("nombre", u.getNombre(), "rol", u.getRol().name())));
        return UsuarioDto.de(u);
    }

    /** Edita el nombre a mostrar y el rol. Un cambio de rol revoca las sesiones del usuario. */
    @Transactional
    public UsuarioDto editar(Long id, String nombre, String rol) {
        UsuarioEntity u = buscar(id);
        if (u.getEstado() == EstadoUsuario.BAJA) {
            throw new SeguridadExceptions.Conflicto("El usuario '" + u.getUsername() + "' está dado de baja.");
        }
        String nombreNuevo = validarNombre(nombre);
        Rol rolNuevo = parsearRol(rol);
        String nombreAnterior = u.getNombre();
        Rol rolAnterior = u.getRol();
        boolean cambiaRol = rolNuevo != rolAnterior;

        if (cambiaRol && rolAnterior == Rol.ADMINISTRADOR) {
            exigirOtroAdministrador(u);
        }
        u.setNombre(nombreNuevo);
        u.setRol(rolNuevo);
        usuarios.saveAndFlush(u);

        if (!Objects.equals(nombreAnterior, nombreNuevo)) {
            auditoria.registrar(TipoAuditoria.USUARIO_EDITADO, ObjetivoAuditoria.USUARIO, u.getUsername(),
                    cambio("nombre", nombreAnterior, nombreNuevo));
        }
        if (cambiaRol) {
            auditoria.registrar(TipoAuditoria.USUARIO_ROL_CAMBIADO, ObjetivoAuditoria.USUARIO, u.getUsername(),
                    cambio("rol", rolAnterior.name(), rolNuevo.name()));
            sesiones.revocarDeUsuario(u.getId(), null);
        }
        return UsuarioDto.de(u);
    }

    @Transactional
    public UsuarioDto suspender(Long id) {
        UsuarioEntity u = buscar(id);
        exigirQueNoSeaElAutor(u, "suspenderte");
        if (u.getEstado() == EstadoUsuario.BAJA) {
            throw new SeguridadExceptions.Conflicto("El usuario '" + u.getUsername() + "' está dado de baja.");
        }
        if (u.getEstado() == EstadoUsuario.SUSPENDIDO) {
            return UsuarioDto.de(u);
        }
        if (u.getRol() == Rol.ADMINISTRADOR) {
            exigirOtroAdministrador(u);
        }
        return cambiarEstado(u, EstadoUsuario.SUSPENDIDO, TipoAuditoria.USUARIO_SUSPENDIDO, true);
    }

    @Transactional
    public UsuarioDto reactivar(Long id) {
        UsuarioEntity u = buscar(id);
        if (u.getEstado() == EstadoUsuario.BAJA) {
            throw new SeguridadExceptions.Conflicto("El usuario '" + u.getUsername()
                    + "' fue dado de baja: la baja es definitiva y no se puede reactivar.");
        }
        if (u.getEstado() == EstadoUsuario.ACTIVO) {
            return UsuarioDto.de(u);
        }
        return cambiarEstado(u, EstadoUsuario.ACTIVO, TipoAuditoria.USUARIO_REACTIVADO, false);
    }

    @Transactional
    public UsuarioDto baja(Long id) {
        UsuarioEntity u = buscar(id);
        exigirQueNoSeaElAutor(u, "darte de baja");
        if (u.getEstado() == EstadoUsuario.BAJA) {
            return UsuarioDto.de(u);
        }
        if (u.getRol() == Rol.ADMINISTRADOR && u.getEstado() == EstadoUsuario.ACTIVO) {
            exigirOtroAdministrador(u);
        }
        return cambiarEstado(u, EstadoUsuario.BAJA, TipoAuditoria.USUARIO_BAJA, true);
    }

    /** Asigna una contraseña temporal. La contraseña no queda en ningún lado más que su hash. */
    @Transactional
    public void blanquearClave(Long id, String clave) {
        UsuarioEntity u = buscar(id);
        exigirQueNoSeaElAutor(u, "blanquear tu propia contraseña (usá \"Cambiar contraseña\")");
        if (u.getEstado() == EstadoUsuario.BAJA) {
            throw new SeguridadExceptions.Conflicto("El usuario '" + u.getUsername() + "' está dado de baja.");
        }
        ReglasClave.validar(u.getUsername(), clave);
        u.setClaveHash(encoder.encode(clave));
        u.setDebeCambiarClave(true);
        usuarios.saveAndFlush(u);

        auditoria.registrar(TipoAuditoria.USUARIO_CLAVE_BLANQUEADA, ObjetivoAuditoria.USUARIO, u.getUsername(),
                Map.of("nuevo", Map.of("debeCambiarClave", true)));
        sesiones.revocarDeUsuario(u.getId(), null);
    }

    /**
     * Administrador inicial (design D9): si no hay ningún Administrador activo, crea uno marcado
     * para cambiar la contraseña en el primer ingreso. Si el nombre ya lo usa otro usuario (sólo
     * pasa si alguien tocó la base a mano), le agrega un sufijo en vez de pisarlo.
     *
     * @return el usuario creado, o vacío si ya había un Administrador activo
     */
    @Transactional
    public Optional<UsuarioDto> crearAdministradorInicialSiFalta(String username, String clave) {
        if (usuarios.existsByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.ACTIVO)) {
            return Optional.empty();
        }
        String nombre = username.trim().toLowerCase();
        for (int i = 2; usuarios.existsByUsername(nombre); i++) {
            nombre = username.trim().toLowerCase() + "-" + i;
        }
        UsuarioEntity u = new UsuarioEntity();
        u.setUsername(nombre);
        u.setNombre("Administrador");
        u.setRol(Rol.ADMINISTRADOR);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setClaveHash(encoder.encode(clave));
        u.setDebeCambiarClave(true);
        u.setCreadoEn(Instant.now(reloj));
        usuarios.saveAndFlush(u);

        auditoria.registrarComoSistema(TipoAuditoria.ADMIN_INICIAL_CREADO, ObjetivoAuditoria.USUARIO, u.getUsername(),
                Map.of("nuevo", Map.of("nombre", u.getNombre(), "rol", u.getRol().name())));
        return Optional.of(UsuarioDto.de(u));
    }

    // ------------------------------------------------------------------

    private UsuarioDto cambiarEstado(UsuarioEntity u, EstadoUsuario nuevo, TipoAuditoria tipo, boolean revocar) {
        EstadoUsuario anterior = u.getEstado();
        u.setEstado(nuevo);
        usuarios.saveAndFlush(u);
        auditoria.registrar(tipo, ObjetivoAuditoria.USUARIO, u.getUsername(),
                cambio("estado", anterior.name(), nuevo.name()));
        if (revocar) {
            sesiones.revocarDeUsuario(u.getId(), null);
        }
        return UsuarioDto.de(u);
    }

    /**
     * Nunca menos de un Administrador activo. Toma el bloqueo sobre los administradores activos:
     * dos bajas concurrentes de administradores distintos se serializan y la segunda ve que no
     * quedaría ninguno.
     */
    private void exigirOtroAdministrador(UsuarioEntity objetivo) {
        boolean hayOtro = usuarios.bloquearAdministradoresActivos().stream()
                .anyMatch(a -> !a.getId().equals(objetivo.getId()));
        if (!hayOtro) {
            throw new SeguridadExceptions.Conflicto("'" + objetivo.getUsername()
                    + "' es el único Administrador activo: el sistema quedaría sin Administrador. "
                    + "Dale el rol Administrador a otro usuario primero.");
        }
    }

    private static void exigirQueNoSeaElAutor(UsuarioEntity objetivo, String accion) {
        UsuarioSesion.actual()
                .filter(autor -> autor.usuarioId().equals(objetivo.getId()))
                .ifPresent(autor -> {
                    throw new SeguridadExceptions.Conflicto("No podés " + accion + ".");
                });
    }

    private UsuarioEntity buscar(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new SeguridadExceptions.NoEncontrado("El usuario " + id + " no existe."));
    }

    private static String validarNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty() || n.length() > NOMBRE_MAX) {
            throw new SeguridadExceptions.Invalida("El nombre a mostrar es obligatorio (hasta " + NOMBRE_MAX
                    + " caracteres).");
        }
        return n;
    }

    static Rol parsearRol(String rol) {
        try {
            return Rol.valueOf(rol == null ? "" : rol.trim());
        } catch (IllegalArgumentException e) {
            throw new SeguridadExceptions.Invalida("El rol '" + rol + "' no existe.");
        }
    }

    private static Map<String, Object> cambio(String campo, Object anterior, Object nuevo) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("anterior", Map.of(campo, anterior));
        d.put("nuevo", Map.of(campo, nuevo));
        return d;
    }
}
