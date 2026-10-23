package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.CambioEstadoReglaRequest;
import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.overrides.UnknownControllableException;
import com.yerbanalytics.backend.engine.parametros.ReglaEstadoInvalidoException;
import com.yerbanalytics.backend.engine.parametros.ReglasEstadoService;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interruptor operativo de las reglas del motor. Es una acción con efecto inmediato en el próximo pulso, por eso va aparte
 * del guardado en lote de {@code PUT /api/rules/parametros}. Permiso: {@code reglas.editar} (ver {@code MapaPermisos}); el
 * simulador no lo tiene.
 */
@RestController
@RequestMapping("/api/rules")
public class ReglasEstadoController {

    private final ReglasEstadoService service;

    public ReglasEstadoController(ReglasEstadoService service) {
        this.service = service;
    }

    /**
     * Prende o apaga una pieza. {@code 200} con la pieza actualizada (también si ya estaba así: no escribe nada);
     * {@code 404} si no existe; {@code 400} con {@code error} = {@code ACTIVA_REQUERIDA}, {@code MOTIVO_REQUERIDO},
     * {@code CONFIRMACION_REQUERIDA} (con la {@code consecuencia}) o {@code MOTIVO_DEMASIADO_LARGO}.
     */
    @PutMapping("/{name}/estado")
    public ResponseEntity<ReglaCatalogoDto> putEstado(
            @PathVariable String name,
            @RequestBody CambioEstadoReglaRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.cambiar(name, request.activa(), request.motivo(),
                request.confirmaProteccion(), UsuarioSesion.autorDelCambio(usuario)));
    }

    @ExceptionHandler(UnknownControllableException.class)
    public ResponseEntity<Map<String, String>> handleUnknown(UnknownControllableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReglaEstadoInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(ReglaEstadoInvalidoException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", ex.codigo());
        body.put("mensaje", ex.getMessage());
        if (ex.consecuencia() != null) {
            body.put("consecuencia", ex.consecuencia());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
