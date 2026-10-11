package com.yerbanalytics.backend.config;

import com.yerbanalytics.backend.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autenticación de dispositivos de captura: el filtro de la cadena del contrato
 * ({@code /api/camara/v1/**}, ver {@code SeguridadConfig}).
 *
 * <p>Antes era un filtro de servlet suelto, porque el backend no tenía autenticación en ningún
 * otro lado. Con HU-01 pasó a ser un filtro de Spring Security, con la misma lógica: la cadena del
 * contrato no conoce las sesiones de usuario, y la de plataforma no conoce este token. Las
 * credenciales no se cruzan.
 *
 * <p>El header {@code Authorization: Bearer} es la vía canónica en todos los endpoints. El
 * token por query string se admite <strong>únicamente</strong> en el stream SSE, porque
 * {@code EventSource} no permite fijar headers en el navegador — es una limitación del
 * cliente, no del protocolo. Un cliente nativo usa el header en todas las rutas.
 *
 * <p>No es un bean a propósito: si lo fuera, Spring Boot lo registraría además como filtro de
 * servlet global, fuera de la cadena.
 */
public class CamaraAuthFilter extends OncePerRequestFilter {

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

    private final TokenService tokenService;

    public CamaraAuthFilter(TokenService tokenService) {
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
     * dispositivos— y la protege la cadena de plataforma con sesión de usuario y permiso.
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
