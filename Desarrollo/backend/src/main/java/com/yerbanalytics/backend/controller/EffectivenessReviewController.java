package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.EffectivenessReview;
import com.yerbanalytics.backend.dto.NewEffectivenessReview;
import com.yerbanalytics.backend.effectiveness.EffectivenessReviewService;
import com.yerbanalytics.backend.exception.EffectivenessEventNotFoundException;
import com.yerbanalytics.backend.exception.EffectivenessNotBlockingException;
import com.yerbanalytics.backend.exception.InvalidEffectivenessReviewException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Revisión de una acción que quedó sin efectividad (S-06, reglas_v2 §4): levanta el bloqueo de su repetición autónoma. Quién
 * revisa sale de la sesión, no del cuerpo.
 */
@RestController
@RequestMapping("/api/efectividad/{eventId}/revision")
public class EffectivenessReviewController {

    private final EffectivenessReviewService reviews;

    public EffectivenessReviewController(EffectivenessReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    public ResponseEntity<EffectivenessReview> register(
            @PathVariable String eventId,
            @RequestBody(required = false) NewEffectivenessReview body,
            @RequestHeader(value = "X-Usuario", required = false) String user) {
        String detail = body == null ? null : body.detail();
        return ResponseEntity.ok(reviews.register(eventId, detail, UsuarioSesion.autorDelCambio(user)));
    }

    @ExceptionHandler({InvalidEffectivenessReviewException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(Exception ex) {
        String message = ex instanceof InvalidEffectivenessReviewException ? ex.getMessage()
                : "El cuerpo de la revisión no es un JSON válido (detail).";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(EffectivenessEventNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EffectivenessEventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EffectivenessNotBlockingException.class)
    public ResponseEntity<Map<String, String>> handleNotBlocking(EffectivenessNotBlockingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
