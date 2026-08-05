package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Diagnostico;
import com.yerbanalytics.backend.service.DiagnosticoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
 * que va a usar el servicio de inferencia cuando exista el modelo, y el mismo que usa hoy el
 * panel de simulación para cargar un diagnóstico a mano. No hay endpoint de simulación, no hay
 * parámetro de origen, y el alta <strong>no</strong> depende del modo estático/simulación:
 * el modelo tampoco va a depender de él.
 *
 * <p>El modelo es Keras/Python; aunque corra en la misma máquina, no vive dentro del JVM. El
 * diagnóstico entra por HTTP con o sin simulador.
 */
@RestController
@RequestMapping("/api/diagnosticos")
public class DiagnosticoController {

    private final DiagnosticoService service;

    public DiagnosticoController(DiagnosticoService service) {
        this.service = service;
    }

    /**
     * Alta de un diagnóstico sobre una captura.
     *
     * <p>{@code sectorId} y {@code zonaId} son opcionales: si no vienen, se toman de la
     * captura. El emisor puede sobrescribirlos, pero no puede omitir la captura.
     */
    public record NuevoDiagnostico(
            String capturaId, String sectorId, String zonaId,
            String estado, Double conf, String sev) {}

    @PostMapping
    public ResponseEntity<Diagnostico> alta(@RequestBody NuevoDiagnostico body) {
        Diagnostico d = service.alta(
                body != null ? body.capturaId() : null,
                body != null ? body.sectorId() : null,
                body != null ? body.zonaId() : null,
                body != null ? body.estado() : null,
                body != null ? body.conf() : null,
                body != null ? body.sev() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }

    @GetMapping
    public ResponseEntity<List<Diagnostico>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
