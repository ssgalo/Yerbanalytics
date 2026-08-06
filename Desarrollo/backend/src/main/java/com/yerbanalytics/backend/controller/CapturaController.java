package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.config.CamaraAuthFilter;
import com.yerbanalytics.backend.dto.CapturaCreada;
import com.yerbanalytics.backend.dto.EstadoOrden;
import com.yerbanalytics.backend.model.CapturaEntity;
import com.yerbanalytics.backend.service.CapturaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Órdenes de captura e imágenes.
 *
 * <p>Sirve <strong>dos superficies</strong>, y la separación importa:
 *
 * <ul>
 *   <li>{@code /api/camara/v1/ordenes/**} — el <strong>contrato</strong> del dispositivo:
 *       stream de órdenes, subida de la imagen y acuse de fallo. Lo implementa un cliente
 *       (hoy la PWA; mañana una app Android). Fuente de verdad:
 *       {@code Desarrollo/contratos/camara/v1/openapi.yaml}.</li>
 *   <li>{@code /api/capturas/**} — API de plataforma: emitir una orden, seguirla y servir la
 *       imagen. La consumen el simulador, el dashboard y mañana el planificador.</li>
 * </ul>
 *
 * Un cliente de captura no debe usar nunca las rutas de plataforma.
 */
@RestController
public class CapturaController {

    private final CapturaService service;
    private final ObjectMapper mapper;

    public CapturaController(CapturaService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    // ==================================================================
    // API de plataforma — fuera del contrato
    // ==================================================================

    public record NuevaOrden(String sectorId, Integer posicionRiel) {}

    /**
     * Emisor único de órdenes. Hoy lo llama el panel de simulación; mañana el planificador de
     * pasadas del riel hará exactamente esta misma llamada.
     */
    @PostMapping("/api/capturas/ordenes")
    public ResponseEntity<EstadoOrden> emitir(@RequestBody NuevaOrden body) {
        EstadoOrden orden = service.emitirOrden(
                body != null ? body.sectorId() : null,
                body != null ? body.posicionRiel() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(orden);
    }

    @GetMapping("/api/capturas/ordenes")
    public ResponseEntity<List<EstadoOrden>> ultimas() {
        return ResponseEntity.ok(service.ultimasOrdenes());
    }

    @GetMapping("/api/capturas/ordenes/{ordenId}")
    public ResponseEntity<EstadoOrden> consultar(@PathVariable String ordenId) {
        return ResponseEntity.ok(service.consultarOrden(ordenId));
    }

    /**
     * Sirve el JPEG. El contenido de una captura no cambia nunca, así que se declara
     * inmutable y se valida por ETag (el sha256): el dashboard no vuelve a descargarla.
     */
    @GetMapping("/api/capturas/{capturaId}/imagen")
    public ResponseEntity<byte[]> imagen(@PathVariable String capturaId) {
        CapturaEntity c = service.buscarCaptura(capturaId)
                .orElseThrow(() -> new CapturaService.OrdenInexistenteException(
                        "La captura '" + capturaId + "' no existe."));
        byte[] bytes;
        try {
            bytes = service.leerImagen(c);
        } catch (IOException e) {
            // Fila sin archivo: se reporta como 404 y queda en el log del servidor para
            // detectar la inconsistencia.
            throw new CapturaService.OrdenInexistenteException(
                    "La imagen de la captura '" + capturaId + "' no está disponible en disco.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .eTag("\"" + c.getSha256() + "\"")
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(bytes);
    }

    // ==================================================================
    // Contrato v1 — lo que implementa un cliente de captura
    // ==================================================================

    /**
     * Canal de órdenes. Al abrirse, el backend drena las órdenes pendientes acumuladas.
     *
     * <p>Acepta el token por header (canónico) o por query string. La segunda vía existe sólo
     * acá y sólo porque {@code EventSource} no permite fijar headers en el navegador; un
     * cliente nativo usa el header. Ver {@code CamaraAuthFilter}.
     */
    @GetMapping(value = "/api/camara/v1/ordenes/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest req, HttpServletResponse res) {
        // Cabeceras anti-buffering. Sin ellas, cualquier proxy intermedio —un túnel, un nginx,
        // un CDN— puede acumular la respuesta esperando a que "termine", y como un stream SSE
        // no termina nunca, las órdenes no llegan jamás al dispositivo. El síntoma es una
        // conexión abierta y silenciosa, que es de lo más difícil de diagnosticar.
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        res.setHeader("X-Accel-Buffering", "no");
        res.setHeader(HttpHeaders.CONNECTION, "keep-alive");

        String dispositivoId = (String) req.getAttribute(CamaraAuthFilter.ATTR_DISPOSITIVO);
        return service.abrirCanal(dispositivoId);
    }

    /**
     * Subida de la imagen de una orden.
     *
     * <p>El {@code 409} no es un error: significa que una subida anterior sí llegó y se perdió
     * la respuesta. Devuelve el {@code capturaId} existente y el contrato obliga al cliente a
     * tratarlo como éxito. Sin esta regla, un corte de red en el momento justo produce
     * duplicados o falsos fallos.
     */
    @PostMapping(value = "/api/camara/v1/ordenes/{ordenId}/imagen",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CapturaCreada> subirImagen(@PathVariable String ordenId,
                                                     @RequestPart("imagen") MultipartFile imagen,
                                                     @RequestPart("meta") String metaJson,
                                                     HttpServletRequest req) throws IOException {
        String dispositivoId = (String) req.getAttribute(CamaraAuthFilter.ATTR_DISPOSITIVO);
        Meta meta = parsearMeta(metaJson);

        CapturaCreada creada = service.recibirImagen(
                ordenId, dispositivoId, imagen.getBytes(),
                meta.ancho(), meta.alto(), meta.sha256(), meta.capturadaEn(),
                meta.constraints() != null ? mapper.writeValueAsString(meta.constraints()) : null);

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    public record Meta(Integer ancho, Integer alto, String sha256, Long capturadaEn,
                       Map<String, Object> constraints) {}

    private Meta parsearMeta(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Falta la metadata de la imagen.");
        }
        Meta m;
        try {
            m = mapper.readValue(json, Meta.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("La metadata de la imagen no es JSON válido.");
        }
        if (m.ancho() == null || m.alto() == null) {
            throw new IllegalArgumentException("La metadata debe incluir ancho y alto.");
        }
        return m;
    }

    public record AcuseFallo(String motivo, String detalle) {}

    @PostMapping("/api/camara/v1/ordenes/{ordenId}/fallo")
    public ResponseEntity<Void> acusarFallo(@PathVariable String ordenId,
                                            @RequestBody AcuseFallo body,
                                            HttpServletRequest req) {
        String dispositivoId = (String) req.getAttribute(CamaraAuthFilter.ATTR_DISPOSITIVO);
        service.acusarFallo(ordenId, dispositivoId,
                body != null ? body.motivo() : null,
                body != null ? body.detalle() : null);
        return ResponseEntity.accepted().build();
    }

    // ==================================================================
    // Errores → códigos HTTP del contrato
    // ==================================================================

    @ExceptionHandler(CapturaService.OrdenInexistenteException.class)
    public ResponseEntity<Map<String, String>> handleNoExiste(CapturaService.OrdenInexistenteException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** 409 idempotente: devuelve la captura que ya existe para que el cliente la dé por buena. */
    @ExceptionHandler(CapturaService.OrdenYaResueltaException.class)
    public ResponseEntity<CapturaCreada> handleYaResuelta(CapturaService.OrdenYaResueltaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getCaptura());
    }

    @ExceptionHandler(CapturaService.OrdenTerminalException.class)
    public ResponseEntity<Map<String, String>> handleTerminal(CapturaService.OrdenTerminalException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CapturaService.DispositivoAjenoException.class)
    public ResponseEntity<Map<String, String>> handleAjeno(CapturaService.DispositivoAjenoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CapturaService.ImagenCorruptaException.class)
    public ResponseEntity<Map<String, String>> handleCorrupta(CapturaService.ImagenCorruptaException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CapturaService.AlmacenamientoFallidoException.class)
    public ResponseEntity<Map<String, String>> handleAlmacenamiento(
            CapturaService.AlmacenamientoFallidoException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
