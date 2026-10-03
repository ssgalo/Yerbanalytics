package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Pasada;
import com.yerbanalytics.backend.service.PasadaRechazadaException;
import com.yerbanalytics.backend.service.PasadaRielService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pasada del riel: iniciar, seguir y cancelar. Capacidad del sistema, no "de demo": funciona
 * siempre; lo que el interruptor Demo Expo oculta es sólo la pestaña del dashboard.
 *
 * <p>Fuera de {@code /api/camara/v1/**}: es API de plataforma, no contrato del dispositivo.
 */
@RestController
@RequestMapping("/api/pasadas")
public class PasadaRielController {

    private final PasadaRielService service;

    public PasadaRielController(PasadaRielService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Pasada> iniciar() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.iniciar());
    }

    /** La pasada en curso o, si ya terminó, la última. 204 si no hubo ninguna desde el arranque. */
    @GetMapping("/actual")
    public ResponseEntity<Pasada> actual() {
        return service.estado()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/actual/cancelar")
    public ResponseEntity<Pasada> cancelar() {
        return ResponseEntity.ok(service.cancelar());
    }

    @ExceptionHandler(PasadaRechazadaException.class)
    public ResponseEntity<Map<String, String>> handleRechazada(PasadaRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
