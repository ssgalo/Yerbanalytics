package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.OrigenPulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.Pulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulsoEnCursoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pulso de sensado manual: pide lectura a todas las macro-zonas ahora, con o sin motor. Es el mismo pulso que el
 * programado (reintentos y escalamiento incluidos) y no corre las franjas. El estado del pulso no se expone: el
 * resultado se ve en el panel general y en el historial.
 */
@RestController
@RequestMapping("/api/pulsos")
public class PulsoController {

    /** Cuerpo del pedido; sin {@code ejecutarMotor} (o sin cuerpo), se ejecuta el motor. */
    public record PedidoPulso(Boolean ejecutarMotor) {
    }

    private final PulsoSensadoService service;

    public PulsoController(PulsoSensadoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Pulso> pedir(@RequestBody(required = false) PedidoPulso pedido) {
        boolean motor = pedido == null || pedido.ejecutarMotor() == null || pedido.ejecutarMotor();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.iniciar(OrigenPulso.MANUAL, motor));
    }

    @ExceptionHandler(PulsoEnCursoException.class)
    public ResponseEntity<Map<String, String>> handleEnCurso(PulsoEnCursoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
