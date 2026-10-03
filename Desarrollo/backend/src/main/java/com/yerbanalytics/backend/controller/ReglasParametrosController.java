package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.CambiosParametrosRequest;
import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ErrorParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosInvalidosException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de parámetros de las reglas del motor: qué umbral usa cada regla y cuánto vale hoy.
 * Lectura del catálogo normalizado y edición en lote, todo o nada. Mismo prefijo que
 * {@link RuleEngineSchemaController}; sin autenticación, igual que {@link ConfiguracionController}.
 */
@RestController
@RequestMapping("/api/rules")
public class ReglasParametrosController {

    private final CatalogoParametrosService service;

    public ReglasParametrosController(CatalogoParametrosService service) {
        this.service = service;
    }

    @GetMapping("/parametros")
    public ResponseEntity<CatalogoReglasDto> getParametros() {
        return ResponseEntity.ok(service.catalogo());
    }

    /** {@code valor = null} en un cambio restablece el valor de fábrica. */
    @PutMapping("/parametros")
    public ResponseEntity<CatalogoReglasDto> putParametros(
            @RequestBody CambiosParametrosRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.guardar(request.cambios(), usuario));
    }

    /** Lote inválido → 400 con un error por parámetro; no se persistió nada. */
    @ExceptionHandler(ParametrosInvalidosException.class)
    public ResponseEntity<Map<String, List<ErrorParametro>>> handleInvalidos(ParametrosInvalidosException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errores", ex.getErrores()));
    }
}
