package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DisposicionTopologia;
import com.yerbanalytics.backend.dto.NuevaTopologia;
import com.yerbanalytics.backend.dto.TopologiaVivero;
import com.yerbanalytics.backend.exception.InvalidTopologyException;
import com.yerbanalytics.backend.exception.TopologyConflictException;
import com.yerbanalytics.backend.service.TopologiaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Generación dinámica de la topología del vivero (HU-18 CA-01). El Administrador define la
 * estructura física (N macro-zonas × M sectores) y el sistema genera la grilla lógica. Un
 * rango inválido responde 400 y una topología ya cargada (sin pedir regenerar) responde 409.
 */
@RestController
@RequestMapping("/api/topologia")
public class TopologiaController {

    private final TopologiaService topologiaService;

    public TopologiaController(TopologiaService topologiaService) {
        this.topologiaService = topologiaService;
    }

    @GetMapping
    public ResponseEntity<TopologiaVivero> getTopologia() {
        return ResponseEntity.ok(topologiaService.getTopologia());
    }

    @PostMapping
    public ResponseEntity<TopologiaVivero> generar(@RequestBody NuevaTopologia dto) {
        return ResponseEntity.ok(topologiaService.generar(dto));
    }

    /** Actualiza la disposición visual por fila sin regenerar la grilla (HU-18 CA-01). */
    @PutMapping("/disposicion")
    public ResponseEntity<TopologiaVivero> actualizarDisposicion(@RequestBody DisposicionTopologia dto) {
        return ResponseEntity.ok(topologiaService.actualizarDisposicion(dto));
    }

    /** Zone/sector count outside valid limits (HU-18 CA-01) → 400. */
    @ExceptionHandler(InvalidTopologyException.class)
    public ResponseEntity<Map<String, String>> handleInvalida(InvalidTopologyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** Topology already loaded and regeneration was not requested (HU-18 CA-01) → 409. */
    @ExceptionHandler(TopologyConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflicto(TopologyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
