package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NurseryData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nursery")
public class NurseryController {

    @GetMapping
    public ResponseEntity<NurseryData> getNurseryData() {
        // TODO: Poblar la respuesta desde la capa de servicios (ej. NurseryService).
        // Por ahora devolvemos null para establecer formalmente el contrato de la API.
        return ResponseEntity.ok(null);
    }
}