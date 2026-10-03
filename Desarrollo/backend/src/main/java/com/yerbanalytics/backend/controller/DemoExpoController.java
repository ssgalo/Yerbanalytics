package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.service.PreferenciaDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Interruptor de la pestaña "Demo Expo". Ruta propia bajo {@code /api/configuracion}: no choca con
 * el {@code PUT /api/configuracion} agronómico (HU-15) ni pasa por su auditoría.
 */
@RestController
@RequestMapping("/api/configuracion/demo-expo")
public class DemoExpoController {

    public record Interruptor(Boolean visible) {}

    private final PreferenciaDashboardService service;

    public DemoExpoController(PreferenciaDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> get() {
        return Map.of("visible", service.demoExpoVisible());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> put(@RequestBody Interruptor body) {
        if (body == null || body.visible() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Falta el campo 'visible'."));
        }
        return ResponseEntity.ok(Map.of("visible", service.cambiarDemoExpo(body.visible())));
    }
}
