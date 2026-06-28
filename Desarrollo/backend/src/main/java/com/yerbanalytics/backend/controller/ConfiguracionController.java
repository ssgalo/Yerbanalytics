package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.service.ConfiguracionInvalidaException;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Configuración agronómica (HU-15). Sólo lectura y actualización completa: el guardado
 * valida contra los límites fisiológicos y, si algo no es válido, responde 400.
 */
@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public ResponseEntity<Configuracion> getConfiguracion() {
        return ResponseEntity.ok(configuracionService.getConfiguracion());
    }

    @PutMapping
    public ResponseEntity<Configuracion> updateConfiguracion(
            @RequestBody Configuracion cfg,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(configuracionService.updateConfiguracion(cfg, usuario));
    }

    /** Validación fisiológica fallida (HU-15 CA-03) → 400 con el mensaje del error. */
    @ExceptionHandler(ConfiguracionInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleInvalida(ConfiguracionInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
