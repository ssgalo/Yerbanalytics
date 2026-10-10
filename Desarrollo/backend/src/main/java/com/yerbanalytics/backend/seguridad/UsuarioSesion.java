package com.yerbanalytics.backend.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Principal de una petición autenticada con sesión de usuario. Lo arma {@link SesionFilter} en
 * cada petición a partir de la fila de la sesión y la del usuario, así que refleja el rol vigente
 * en ese instante (no el que tenía al iniciar sesión).
 *
 * @param sesionHash SHA-256 de la cookie; sirve para excluir la sesión del autor al revocar.
 */
public record UsuarioSesion(Long usuarioId, String username, String nombre, Rol rol,
                            String sesionHash, boolean debeCambiarClave) {

    /** El usuario de la petición en curso, si la autenticó una sesión. */
    public static Optional<UsuarioSesion> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    /**
     * Quién firma un cambio en el historial: el nombre del usuario de la sesión. El header
     * {@code X-Usuario} que mandaba el cliente queda sólo como respaldo para llamadas sin sesión
     * de usuario (tests de controller): con sesión, el cliente ya no puede firmar por otro.
     */
    public static String autorDelCambio(String declaradoPorElCliente) {
        return actual().map(UsuarioSesion::nombre).orElse(declaradoPorElCliente);
    }
}
