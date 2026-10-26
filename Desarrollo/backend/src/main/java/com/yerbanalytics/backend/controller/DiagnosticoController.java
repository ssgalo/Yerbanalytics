package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DetectionsResponse;
import com.yerbanalytics.backend.dto.Diagnostico;
import com.yerbanalytics.backend.exception.DiagnosisNotFoundException;
import com.yerbanalytics.backend.service.DiagnosticoService;
import com.yerbanalytics.backend.service.DiagnosticoService.NuevoDiagnostico;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Alta y consulta de diagnósticos.
 *
 * <p><strong>Un único camino de escritura, sin variantes.</strong> Este endpoint es el mismo
 * que usa el servicio de inferencia y el mismo que usa una carga manual desde el simulador. No
 * hay endpoint alternativo, no hay parámetro de origen y no hay ningún estado global del backend
 * que condicione el alta: el modelo tampoco depende de uno.
 *
 * <p>El modelo corre en Python, fuera del JVM: el diagnóstico entra por HTTP en cualquier caso.
 */
@RestController
@RequestMapping("/api/diagnosticos")
public class DiagnosticoController {

    private final DiagnosticoService service;

    public DiagnosticoController(DiagnosticoService service) {
        this.service = service;
    }

    /**
     * Alta de un diagnóstico sobre una captura (contrato en design D7/D12 de
     * {@code add-diagnostico-por-detecciones}).
     *
     * <p>El cuerpo es {@link NuevoDiagnostico}: {@code capturaId}, {@code estado}, {@code sev}
     * (sólo en estados anómalos), {@code confidence} y {@code conf} (salvo en No concluyente),
     * {@code detections} (opcional) y {@code crop} (opcional: la región analizada; con él, las cajas
     * son relativas a esa región, design D5 de {@code add-tiling-inferencia}). {@code sectorId} y {@code zonaId} son opcionales: si no
     * vienen, se toman de la captura. El emisor puede sobrescribirlos, pero no puede omitir la
     * captura.
     */
    @PostMapping
    public ResponseEntity<Diagnostico> alta(@RequestBody(required = false) NuevoDiagnostico body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.alta(body));
    }

    @GetMapping
    public ResponseEntity<List<Diagnostico>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /**
     * Las cajas de un diagnóstico persistido y su recorte: no viajan en el listado ni en el snapshot
     * (design D8).
     */
    @GetMapping("/{id}/detecciones")
    public ResponseEntity<DetectionsResponse> detecciones(@PathVariable String id) {
        return ResponseEntity.ok(service.detecciones(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** Cuerpo que no se puede leer (p. ej. una caja con un valor que no es número). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El cuerpo del diagnóstico no es un JSON válido para el alta."));
    }

    @ExceptionHandler(DiagnosisNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoEncontrado(DiagnosisNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
