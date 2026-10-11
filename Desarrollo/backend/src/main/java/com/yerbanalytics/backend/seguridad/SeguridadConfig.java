package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.config.CamaraAuthFilter;
import com.yerbanalytics.backend.service.TokenService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Dos cadenas de Spring Security (design D3).
 *
 * <ol>
 *   <li>{@code @Order(1)}, {@code /api/camara/v1/**}: el contrato del dispositivo de captura, con
 *       su token propio ({@link CamaraAuthFilter}). Comportamiento idéntico al de antes de HU-01;
 *       la suite de conformidad del contrato es la prueba.</li>
 *   <li>{@code @Order(2)}, todo lo demás: sesión de usuario ({@link SesionFilter}) y autorización
 *       por permiso según {@link MapaPermisos}, con {@code denyAll()} al final.</li>
 * </ol>
 *
 * <p>Qué se apaga de lo que Spring Security trae por defecto, y por qué:
 * <ul>
 *   <li><b>CSRF</b>: la cookie de sesión es {@code SameSite=Strict} y CORS está acotado a la LAN;
 *       un token de CSRF no agrega nada y obligaría a todos los clientes a manejarlo.</li>
 *   <li><b>formLogin, httpBasic, logout</b>: el login es {@code POST /api/auth/login} con JSON y el
 *       cierre {@code POST /api/auth/logout}. Sin redirecciones ni formularios de Spring.</li>
 *   <li><b>Cache-Control</b>: Spring fija {@code no-store} en todo, y eso rompe el {@code ETag} de
 *       las imágenes de captura. Cada controller sigue fijando sus propios headers de caché.</li>
 *   <li><b>Sesión HTTP</b>: {@code STATELESS}. La sesión es la nuestra (tabla {@code sesion}), no
 *       la del contenedor.</li>
 * </ul>
 */
@Configuration
public class SeguridadConfig {

    /** BCrypt costo 10: vuelve lenta la fuerza bruta (no hay bloqueo por intentos; ver design). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain cadenaContratoCamara(HttpSecurity http, TokenService tokenService) throws Exception {
        base(http.securityMatcher("/api/camara/v1/**"))
                .addFilterBefore(new CamaraAuthFilter(tokenService), AnonymousAuthenticationFilter.class)
                // El filtro decide (token o rutas de enrolamiento); la autorización no agrega reglas.
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain cadenaPlataforma(HttpSecurity http, SesionService sesiones,
                                                RolPermisoService permisos, CookieSesion cookie) throws Exception {
        base(http)
                .addFilterBefore(new SesionFilter(sesiones, permisos, cookie), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(this::reglas)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                RespuestasSeguridad.sinSesion(res, SesionService.MotivoRechazo.SIN_SESION))
                        .accessDeniedHandler((req, res, ex) -> RespuestasSeguridad.sinPermiso(res,
                                MapaPermisos.reglaPara(req)
                                        .map(MapaPermisos.Regla::acceso)
                                        .filter(MapaPermisos.Acceso.ConPermiso.class::isInstance)
                                        .map(a -> ((MapaPermisos.Acceso.ConPermiso) a).permiso())
                                        .orElse(null))));
        return http.build();
    }

    private void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry a) {
        // Los despachos internos (la página de error, la reanudación de una petición asíncrona) ya
        // pasaron la autorización en el despacho original.
        a.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll();
        for (MapaPermisos.Regla r : MapaPermisos.reglas()) {
            var regla = a.requestMatchers(r.matcher());
            if (r.acceso() instanceof MapaPermisos.Acceso.Publico) {
                regla.permitAll();
            } else if (r.acceso() instanceof MapaPermisos.Acceso.Sesion) {
                regla.authenticated();
            } else if (r.acceso() instanceof MapaPermisos.Acceso.ConPermiso p) {
                regla.hasAuthority(p.permiso().getCodigo());
            }
        }
        a.anyRequest().denyAll();
    }

    private static HttpSecurity base(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.cacheControl(c -> c.disable()));
    }
}
