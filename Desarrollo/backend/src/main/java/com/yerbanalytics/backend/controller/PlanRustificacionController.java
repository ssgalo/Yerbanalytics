package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NuevoPlanRustificacion;
import com.yerbanalytics.backend.dto.PlanRustificacion;
import com.yerbanalytics.backend.exception.PlanRustificacionConflictoException;
import com.yerbanalytics.backend.exception.PlanRustificacionInvalidoException;
import com.yerbanalytics.backend.mediasombra.PlanRustificacionService;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Plan de rustificación de una macro-zona: se ve, se inicia y se elimina desde el detalle de la zona. Quien abre y
 * cierra la malla según el plan es el horario de la mediasombra, no esta API. Quién lo cambia sale de la sesión.
 */
@RestController
@RequestMapping("/api/zonas/{zonaId}/rustificacion")
public class PlanRustificacionController {

    private final PlanRustificacionService planes;

    public PlanRustificacionController(PlanRustificacionService planes) {
        this.planes = planes;
    }

    /** El plan vigente, o {@code 204} si la zona no tiene (rige el horario por defecto). */
    @GetMapping
    public ResponseEntity<PlanRustificacion> vigente(@PathVariable String zonaId) {
        return planes.vigente(zonaId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<PlanRustificacion> iniciar(
            @PathVariable String zonaId,
            @RequestBody(required = false) NuevoPlanRustificacion body,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(planes.iniciar(zonaId, body, UsuarioSesion.autorDelCambio(usuario)));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<PlanRustificacion> cancelar(
            @PathVariable String zonaId,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(planes.cancelar(zonaId, UsuarioSesion.autorDelCambio(usuario)));
    }

    @ExceptionHandler({PlanRustificacionInvalidoException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalido(Exception ex) {
        String message = ex instanceof PlanRustificacionInvalidoException ? ex.getMessage()
                : "El plan debe traer fechaInicio (YYYY-MM-DD) y etapas con dias, abiertaDesde y abiertaHasta.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(PlanRustificacionConflictoException.class)
    public ResponseEntity<Map<String, String>> handleConflicto(PlanRustificacionConflictoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
