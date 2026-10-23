package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.FungicideReview;
import com.yerbanalytics.backend.dto.NewFungicideReview;
import com.yerbanalytics.backend.exception.InvalidFungicideReviewException;
import com.yerbanalytics.backend.exception.ReviewSectorNotFoundException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import com.yerbanalytics.backend.supply.FungicideReviewService;
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
 * Revisión de un sector que alcanzó el tope de fitosanitarios (F-06, reglas_v2 §8.5): levanta el bloqueo de la
 * siguiente aplicación. Quién revisa sale de la sesión, no del cuerpo.
 */
@RestController
@RequestMapping("/api/sectores/{sectorId}/revision-fitosanitario")
public class FungicideReviewController {

    private final FungicideReviewService reviews;

    public FungicideReviewController(FungicideReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    public ResponseEntity<FungicideReview> register(
            @PathVariable String sectorId,
            @RequestBody(required = false) NewFungicideReview body,
            @RequestHeader(value = "X-Usuario", required = false) String user) {
        String detail = body == null ? null : body.detail();
        return ResponseEntity.ok(reviews.register(sectorId, detail, UsuarioSesion.autorDelCambio(user)));
    }

    @ExceptionHandler({InvalidFungicideReviewException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(Exception ex) {
        String message = ex instanceof InvalidFungicideReviewException ? ex.getMessage()
                : "El cuerpo de la revisión no es un JSON válido (detail).";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(ReviewSectorNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ReviewSectorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
