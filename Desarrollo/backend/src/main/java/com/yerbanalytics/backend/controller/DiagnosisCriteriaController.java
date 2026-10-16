package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DiagnosisCriteria;
import com.yerbanalytics.backend.exception.InvalidDiagnosisCriteriaException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import com.yerbanalytics.backend.service.DiagnosisCriteriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Criterio agronómico del diagnóstico: bandas de severidad por estado anómalo y orden de
 * criticidad (design D5/D12 de {@code add-diagnostico-por-detecciones}).
 *
 * <p>Recurso propio y no parte de {@code /api/configuracion}: el servicio de inferencia lo lee
 * con su cuenta de rol Servicio, que tiene {@code diagnosticos.ver} pero no
 * {@code configuracion.ver}. Editarlo pide {@code configuracion.editar} (ver {@code MapaPermisos}).
 */
@RestController
@RequestMapping("/api/configuracion/diagnostico")
public class DiagnosisCriteriaController {

    private final DiagnosisCriteriaService service;

    public DiagnosisCriteriaController(DiagnosisCriteriaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<DiagnosisCriteria> get() {
        return ResponseEntity.ok(service.get());
    }

    /** Reemplaza el criterio completo y devuelve el guardado. */
    @PutMapping
    public ResponseEntity<DiagnosisCriteria> replace(
            @RequestBody(required = false) DiagnosisCriteria body,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.replace(body, UsuarioSesion.autorDelCambio(usuario)));
    }

    @ExceptionHandler(InvalidDiagnosisCriteriaException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(InvalidDiagnosisCriteriaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El criterio no es un JSON válido: las bandas deben ser números enteros de hojas."));
    }
}
