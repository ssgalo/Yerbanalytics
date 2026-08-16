package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.config.CamaraAuthFilter;
import com.yerbanalytics.backend.dto.ConfigCaptura;
import com.yerbanalytics.backend.dto.DispositivoCamara;
import com.yerbanalytics.backend.service.DispositivoCamaraService;
import com.yerbanalytics.backend.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Credenciales y operación del dispositivo de captura.
 *
 * <p>Este controller sirve <strong>dos superficies distintas</strong> y conviene no
 * confundirlas:
 *
 * <ul>
 *   <li>{@code /api/camara/v1/**} — el <strong>contrato</strong>. Es lo que implementa un
 *       cliente (hoy la PWA; mañana una app Android). Definido en
 *       {@code Desarrollo/contratos/camara/v1/openapi.yaml}, que es la fuente de verdad.</li>
 *   <li>{@code /api/camara/vinculacion} y {@code /api/camara/dispositivos} — API de
 *       plataforma. La consume el backoffice, no el dispositivo.</li>
 * </ul>
 *
 * Agregar rutas al primer grupo cambia el contrato y exige actualizar el OpenAPI.
 */
@RestController
public class DispositivoCamaraController {

    private final DispositivoCamaraService service;
    private final TokenService tokenService;

    public DispositivoCamaraController(DispositivoCamaraService service, TokenService tokenService) {
        this.service = service;
        this.tokenService = tokenService;
    }

    // ==================================================================
    // API de plataforma — fuera del contrato
    // ==================================================================

    /** Emite el código de un solo uso que el operario tipea en el dispositivo. */
    @PostMapping("/api/camara/vinculacion")
    public ResponseEntity<Map<String, Object>> generarCodigo() {
        DispositivoCamaraService.CodigoEmitido c = service.generarCodigoVinculacion();
        return ResponseEntity.ok(Map.of("codigo", c.codigo(), "expiraEn", c.expiraEn()));
    }

    @GetMapping("/api/camara/dispositivos")
    public ResponseEntity<List<DispositivoCamara>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /** Baja lógica del dispositivo: invalida su credencial en la siguiente renovación. */
    @DeleteMapping("/api/camara/dispositivos/{id}")
    public ResponseEntity<Void> revocar(@PathVariable String id) {
        service.revocar(id);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // Contrato v1 — lo que implementa un cliente
    // ==================================================================

    public record EnrolarRequest(String codigo, String nombre, String plataforma) {}

    public record EnrolarResponse(String dispositivoId, String refreshToken) {}

    @PostMapping("/api/camara/v1/enrolar")
    public ResponseEntity<EnrolarResponse> enrolar(@RequestBody EnrolarRequest body) {
        DispositivoCamaraService.Enrolado e = service.enrolar(
                body != null ? body.codigo() : null,
                body != null ? body.nombre() : null,
                body != null ? body.plataforma() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EnrolarResponse(e.dispositivoId(), e.refreshToken()));
    }

    public record TokenRequest(String refreshToken) {}

    public record TokenResponse(String accessToken, int expiraEnSeg) {}

    @PostMapping("/api/camara/v1/token")
    public ResponseEntity<TokenResponse> token(@RequestBody TokenRequest body) {
        String access = service.emitirAccessToken(body != null ? body.refreshToken() : null);
        return ResponseEntity.ok(new TokenResponse(access, tokenService.getVidaSeg()));
    }

    @GetMapping("/api/camara/v1/config")
    public ResponseEntity<ConfigCaptura> config() {
        return ResponseEntity.ok(service.config());
    }

    public record HeartbeatRequest(
            Boolean capturaListo, Integer capturasOk, Integer capturasError,
            Integer pendientesEnvio, String detalle) {}

    @PostMapping("/api/camara/v1/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody HeartbeatRequest body, HttpServletRequest req) {
        String dispositivoId = (String) req.getAttribute(CamaraAuthFilter.ATTR_DISPOSITIVO);
        service.registrarHeartbeat(
                dispositivoId,
                body != null && Boolean.TRUE.equals(body.capturaListo()),
                body != null ? body.capturasOk() : null,
                body != null ? body.capturasError() : null);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // Errores → códigos HTTP del contrato
    // ==================================================================

    @ExceptionHandler(DispositivoCamaraService.CredencialInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleCredencial(
            DispositivoCamaraService.CredencialInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
