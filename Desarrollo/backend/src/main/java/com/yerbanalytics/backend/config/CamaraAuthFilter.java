package com.yerbanalytics.backend.config;

import com.yerbanalytics.backend.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autenticación de dispositivos de captura, <strong>acotada por path</strong>.
 *
 * <p>Deliberadamente no se usa {@code spring-boot-starter-security}: el backend no tiene
 * autenticación en ningún otro lado, y traer la cadena de filtros completa protegería por
 * defecto endpoints que hoy son abiertos ({@code /api/nursery}, {@code /api/historial},
 * {@code /api/configuracion}…). Este filtro toca sólo las rutas de cámara y captura; el resto
 * de la API queda exactamente como estaba. Cuando llegue HU-01 habrá que unificar.
 *
 * <p>El header {@code Authorization: Bearer} es la vía canónica en todos los endpoints. El
 * token por query string se admite <strong>únicamente</strong> en el stream SSE, porque
 * {@code EventSource} no permite fijar headers en el navegador — es una limitación del
 * cliente, no del protocolo. Un cliente nativo usa el header en todas las rutas.
 */
@Configuration
public class CamaraAuthFilter {

    /** Prefijo de la superficie del contrato. Todo lo demás es API de plataforma. */
    private static final String CONTRATO = "/api/camara/v1/";

    /**
     * Rutas del contrato que no pueden exigir token, porque son las que lo consiguen:
     * {@code enrolar} consume el código de vinculación y {@code token} canjea la credencial.
     */
    private static final List<String> SIN_TOKEN = List.of(
            "/api/camara/v1/enrolar",
            "/api/camara/v1/token"
    );

    /** Único endpoint donde se admite el token por query string. */
    private static final String STREAM = "/api/camara/v1/ordenes/stream";

    /** Atributo donde queda el dispositivo autenticado, para que los controllers lo lean. */
    public static final String ATTR_DISPOSITIVO = "camara.dispositivoId";

    @Bean
    public FilterRegistrationBean<Filtro> camaraAuthFilterRegistration(TokenService tokenService) {
        FilterRegistrationBean<Filtro> reg = new FilterRegistrationBean<>(new Filtro(tokenService));
        // Sólo estas dos familias de rutas. El resto de /api/** no pasa por acá.
        reg.addUrlPatterns("/api/camara/*", "/api/capturas/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    public static class Filtro extends OncePerRequestFilter {

        private final TokenService tokenService;

        public Filtro(TokenService tokenService) {
            this.tokenService = tokenService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {

            String path = req.getRequestURI();

            // El preflight de CORS nunca lleva Authorization.
            if (HttpMethod.OPTIONS.matches(req.getMethod()) || !exigeToken(path)) {
                chain.doFilter(req, res);
                return;
            }

            Optional<String> dispositivo = extraerToken(req, path).flatMap(tokenService::verificar);
            if (dispositivo.isEmpty()) {
                rechazar(res);
                return;
            }

            req.setAttribute(ATTR_DISPOSITIVO, dispositivo.get());
            chain.doFilter(req, res);
        }

        private Optional<String> extraerToken(HttpServletRequest req, String path) {
            String header = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return Optional.of(header.substring(7).trim());
            }
            // Alternativa acotada al stream: EventSource no puede fijar headers.
            if (STREAM.equals(path)) {
                return Optional.ofNullable(req.getParameter("token"));
            }
            return Optional.empty();
        }

        /**
         * Sólo la superficie del contrato exige token de dispositivo.
         *
         * <p>Todo lo que no está bajo {@code /api/camara/v1/} es API de plataforma —emitir una
         * orden, seguirla, servir la imagen, generar un código de vinculación, listar
         * dispositivos— y la consumen el dashboard y el simulador, que no son dispositivos de
         * captura. Queda abierta como el resto de la API.
         *
         * <p>La regla se expresa por prefijo, no por lista blanca, para que agregar un endpoint
         * al contrato lo deje protegido por omisión en vez de abierto por descuido.
         */
        private static boolean exigeToken(String path) {
            return path.startsWith(CONTRATO) && !SIN_TOKEN.contains(path);
        }

        private static void rechazar(HttpServletResponse res) throws IOException {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write(
                    "{\"error\":\"Token ausente, vencido o inválido. Renová la credencial y reintentá.\"}");
        }
    }
}
