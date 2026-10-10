package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Filtro de sesión de la cadena de plataforma (design D3). Resuelve la cookie y arma un
 * {@code Authentication} cuyas <em>authorities</em> son los permisos efectivos del rol vigente.
 *
 * <ul>
 *   <li>Sin cookie: sigue de largo; si la ruta lo exige, el punto de entrada responde
 *       {@code 401 SIN_SESION}.</li>
 *   <li>Cookie vencida, revocada o cerrada: {@code 401} con su motivo, y borra la cookie. Salvo en
 *       una ruta pública: una cookie vieja no puede impedir volver a iniciar sesión.</li>
 *   <li>Contraseña temporal: {@code 403 CAMBIO_CLAVE_REQUERIDO} en todo lo que no sea cambiarla,
 *       ver el perfil propio o cerrar sesión.</li>
 * </ul>
 *
 * <p>No es un bean a propósito: si lo fuera, Spring Boot lo registraría además como filtro de
 * servlet global, fuera de la cadena.
 */
public class SesionFilter extends OncePerRequestFilter {

    /** Lo único que puede hacer quien tiene una contraseña temporal. */
    private static final List<String> CON_CLAVE_TEMPORAL = List.of(
            "GET /api/auth/perfil", "PUT /api/auth/clave", "POST /api/auth/logout", "POST /api/auth/actividad");

    private final SesionService sesiones;
    private final RolPermisoService permisos;
    private final CookieSesion cookie;

    public SesionFilter(SesionService sesiones, RolPermisoService permisos, CookieSesion cookie) {
        this.sesiones = sesiones;
        this.permisos = permisos;
        this.cookie = cookie;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String valor = CookieSesion.leer(req);
        if (valor == null) {
            chain.doFilter(req, res);
            return;
        }

        SesionService.Resolucion r = sesiones.resolver(valor, cuentaComoActividad(req));
        if (r instanceof SesionService.Resolucion.Rechazada rechazo) {
            if (MapaPermisos.esPublica(req)) {
                chain.doFilter(req, res);
                return;
            }
            res.addHeader(HttpHeaders.SET_COOKIE, cookie.borrar().toString());
            RespuestasSeguridad.sinSesion(res, rechazo.motivo());
            return;
        }

        UsuarioSesion usuario = ((SesionService.Resolucion.Valida) r).usuario();
        if (usuario.debeCambiarClave() && !CON_CLAVE_TEMPORAL.contains(req.getMethod() + " " + req.getRequestURI())
                && !MapaPermisos.esPublica(req)) {
            RespuestasSeguridad.cambioClaveRequerido(res);
            return;
        }

        Set<String> efectivos = permisos.permisosDe(usuario.rol());
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, efectivos.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        chain.doFilter(req, res);
    }

    /**
     * Sólo lo explícito renueva la sesión (design D4): toda petición que no sea de lectura. Los
     * {@code GET} —los sondeos del dashboard, entre ellos— no cuentan; el dashboard manda
     * {@code POST /api/auth/actividad} cuando detecta interacción real.
     */
    private static boolean cuentaComoActividad(HttpServletRequest req) {
        String m = req.getMethod();
        return !HttpMethod.GET.matches(m) && !HttpMethod.HEAD.matches(m) && !HttpMethod.OPTIONS.matches(m);
    }
}
