package com.yerbanalytics.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/nursery")
public class NurseryController {

    @GetMapping
    public ResponseEntity<?> getNurseryData() {
        // TODO: Mapear la data de dominio "NurseryData" de frontend/src/types/domain.ts
        // Temporalmente devolvemos un JSON de estado para validar que el servidor responde.
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Backend inicializado correctamente.",
                "note", "Falta implementar el DTO NurseryData esperado por el frontend."
        ));
    }
}