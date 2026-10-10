package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.IniciarSecuencia;
import com.yerbanalytics.backend.dto.Secuencia;
import com.yerbanalytics.backend.service.SecuenciaInvalidaException;
import com.yerbanalytics.backend.service.SecuenciaRechazadaException;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Secuencias guionadas (riego, mediasombra, lectura): iniciar, seguir y cancelar. Un solo recurso para
 * las tres, igual que la pasada: hay una secuencia actual, y seguirla o cancelarla es lo mismo cualquiera
 * sea el tipo. Capacidad del sistema, no "de demo": funciona siempre; lo que el interruptor Demo Expo
 * oculta es sólo la pestaña del dashboard.
 *
 * <p>Fuera de {@code /api/camara/v1/**}: es API de plataforma, no contrato del dispositivo.
 */
@RestController
@RequestMapping("/api/secuencias")
public class SecuenciaController {

    private final SecuenciaService service;

    public SecuenciaController(SecuenciaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Secuencia> iniciar(@RequestBody IniciarSecuencia pedido) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.iniciar(pedido));
    }

    /** La secuencia en curso o, si ya terminó, la última. 204 si no hubo ninguna desde el arranque. */
    @GetMapping("/actual")
    public ResponseEntity<Secuencia> actual() {
        return service.estado()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/actual/cancelar")
    public ResponseEntity<Secuencia> cancelar() {
        return ResponseEntity.ok(service.cancelar());
    }

    @ExceptionHandler(SecuenciaInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleInvalida(SecuenciaInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SecuenciaRechazadaException.class)
    public ResponseEntity<Map<String, String>> handleRechazada(SecuenciaRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
