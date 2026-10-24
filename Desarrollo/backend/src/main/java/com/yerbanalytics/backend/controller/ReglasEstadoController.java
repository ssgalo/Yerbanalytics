package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.CambioEstadoReglaRequest;
import com.yerbanalytics.backend.dto.CambioEtiquetaReglaRequest;
import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.overrides.UnknownControllableException;
import com.yerbanalytics.backend.engine.parametros.ReglaConflictoException;
import com.yerbanalytics.backend.engine.parametros.ReglaEstadoInvalidoException;
import com.yerbanalytics.backend.engine.parametros.ReglasEstadoService;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interruptor operativo de las reglas del motor, su nombre propio y su eliminación de la vista. Son acciones con efecto
 * inmediato, por eso van aparte del guardado en lote de {@code PUT /api/rules/parametros}. Permiso: {@code reglas.editar}
 * (ver {@code MapaPermisos}); el simulador no lo tiene.
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
     * {@code CONFIRMACION_REQUERIDA} (con la {@code consecuencia}) o {@code MOTIVO_DEMASIADO_LARGO}; {@code 409}
     * {@code REGLA_ELIMINADA} si se pide prender una pieza eliminada.
     */
    @PutMapping("/{name}/estado")
    public ResponseEntity<ReglaCatalogoDto> putEstado(
            @PathVariable String name,
            @RequestBody CambioEstadoReglaRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.cambiar(name, request.activa(), request.motivo(),
                request.confirmaProteccion(), UsuarioSesion.autorDelCambio(usuario)));
    }

    /**
     * Nombre propio de una pieza ({@code etiqueta} vacía o {@code null} restablece el de fábrica). {@code 200} con la
     * pieza; {@code 404} si no existe o no tiene interruptor; {@code 400} {@code ETIQUETA_DEMASIADO_LARGA}.
     */
    @PutMapping("/{name}/etiqueta")
    public ResponseEntity<ReglaCatalogoDto> putEtiqueta(
            @PathVariable String name,
            @RequestBody(required = false) CambioEtiquetaReglaRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.renombrar(name, request != null ? request.etiqueta() : null,
                UsuarioSesion.autorDelCambio(usuario)));
    }

    /**
     * Elimina la pieza de la vista y la apaga. {@code 200} con la pieza ({@code eliminada: true}); {@code 404} si no
     * existe o no tiene interruptor; {@code 409} {@code PROTECCION_NO_ELIMINABLE} si es una protección.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<ReglaCatalogoDto> delete(
            @PathVariable String name,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.eliminar(name, UsuarioSesion.autorDelCambio(usuario)));
    }

    /** Devuelve a la vista una pieza eliminada, apagada. {@code 200} con la pieza; {@code 404} si no existe. */
    @PostMapping("/{name}/restaurar")
    public ResponseEntity<ReglaCatalogoDto> restaurar(
            @PathVariable String name,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        return ResponseEntity.ok(service.restaurar(name, UsuarioSesion.autorDelCambio(usuario)));
    }

    @ExceptionHandler(ReglaConflictoException.class)
    public ResponseEntity<Map<String, String>> handleConflicto(ReglaConflictoException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", ex.codigo());
        body.put("mensaje", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
