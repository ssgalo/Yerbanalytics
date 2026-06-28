package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.HistorialEvento;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta del historial de acciones (HU-11). Solo lectura: no expone verbos de
 * escritura, garantizando la inalterabilidad del registro.
 */
@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final HistorialService historialService;

    public HistorialController(HistorialService historialService) {
        this.historialService = historialService;
    }

    @GetMapping
    public ResponseEntity<List<HistorialEvento>> getHistorial(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String zona,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long desde,
            @RequestParam(required = false) Long hasta) {
        return ResponseEntity.ok(historialService.getHistorial(sector, zona, tipo, desde, hasta));
    }
}
