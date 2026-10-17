package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.ManualLock;
import com.yerbanalytics.backend.dto.NewManualLock;
import com.yerbanalytics.backend.exception.InvalidManualLockException;
import com.yerbanalytics.backend.exception.ManualLockConflictException;
import com.yerbanalytics.backend.exception.ManualLockNotFoundException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import com.yerbanalytics.backend.service.ManualLockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Bloqueo manual de la actuación autónoma (HU-19). Sólo se crea y se reanuda: no hay edición.
 * Quién bloquea sale de la sesión, no del cuerpo.
 */
@RestController
@RequestMapping("/api/bloqueos")
public class ManualLockController {

    private final ManualLockService service;

    public ManualLockController(ManualLockService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ManualLock>> list(@RequestParam(value = "zona", required = false) String zone,
                                                 @RequestParam(value = "sector", required = false) String sector) {
        return ResponseEntity.ok(service.list(zone, sector));
    }

    @PostMapping
    public ResponseEntity<ManualLock> activate(
            @RequestBody(required = false) NewManualLock body,
            @RequestHeader(value = "X-Usuario", required = false) String user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.activate(body, UsuarioSesion.autorDelCambio(user)));
    }

    /** Reanuda (desactiva) el bloqueo; el registro se conserva para saber quién y cuándo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> release(@PathVariable Long id,
                                        @RequestHeader(value = "X-Usuario", required = false) String user) {
        service.release(id, UsuarioSesion.autorDelCambio(user));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({InvalidManualLockException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(Exception ex) {
        String message = ex instanceof InvalidManualLockException
                ? ex.getMessage() : "El cuerpo del bloqueo no es un JSON válido.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(ManualLockNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ManualLockNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ManualLockConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ManualLockConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
