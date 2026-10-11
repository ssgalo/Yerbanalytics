package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.seguridad.AuthService;
import com.yerbanalytics.backend.seguridad.CookieSesion;
import com.yerbanalytics.backend.seguridad.SesionService;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sesión del usuario (HU-01). El login entrega la sesión en una cookie {@code HttpOnly} y
 * devuelve el perfil, nunca el identificador: ni el JavaScript del dashboard lo ve.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mismo cuerpo para toda falla de login: no revela qué falló (HU-01 CA-02). */
    private static final Map<String, String> CREDENCIALES_INCORRECTAS = Map.of("error", "Credenciales incorrectas");

    private final AuthService auth;
    private final SesionService sesiones;
    private final CookieSesion cookie;

    public AuthController(AuthService auth, SesionService sesiones, CookieSesion cookie) {
        this.auth = auth;
        this.sesiones = sesiones;
        this.cookie = cookie;
    }

    public record LoginRequest(String username, String clave) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body, HttpServletRequest req) {
        return auth.login(body != null ? body.username() : null, body != null ? body.clave() : null,
                        req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT))
                .<ResponseEntity<?>>map(ok -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, cookie.emitir(ok.sesionId()).toString())
                        .body(ok.perfil()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CREDENCIALES_INCORRECTAS));
    }

    /** No cuenta como actividad: es un {@code GET}. */
    @GetMapping("/perfil")
    public AuthService.PerfilSesion perfil(@AuthenticationPrincipal UsuarioSesion usuario) {
        return auth.perfil(usuario.usuarioId());
    }

    /**
     * Señal de actividad real (design D4). No hace nada más: el filtro de sesión ya la registró al
     * ver un {@code POST}.
     */
    @PostMapping("/actividad")
    public ResponseEntity<Void> actividad() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UsuarioSesion usuario) {
        sesiones.cerrarPorLogout(usuario.sesionHash());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
                .build();
    }

    public record CambioClaveRequest(String actual, String nueva) {}

    @PutMapping("/clave")
    public ResponseEntity<Void> cambiarClave(@AuthenticationPrincipal UsuarioSesion usuario,
                                             @RequestBody(required = false) CambioClaveRequest body) {
        auth.cambiarClave(usuario, body != null ? body.actual() : null, body != null ? body.nueva() : null);
        return ResponseEntity.noContent().build();
    }
}
