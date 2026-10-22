package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NewTankLoad;
import com.yerbanalytics.backend.dto.TankLevel;
import com.yerbanalytics.backend.exception.InvalidTankLoadException;
import com.yerbanalytics.backend.exception.TankZoneNotFoundException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import com.yerbanalytics.backend.supply.Supply;
import com.yerbanalytics.backend.supply.TankService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * Tanques de insumo: el stock por macro-zona e insumo y el registro de sus cargas (reglas_v2 §8.3). El descuento no
 * pasa por acá: lo hace cada aplicación. Quién carga sale de la sesión, no del cuerpo.
 */
@RestController
@RequestMapping("/api/tanques")
public class SupplyTankController {

    private final TankService tanks;

    public SupplyTankController(TankService tanks) {
        this.tanks = tanks;
    }

    @GetMapping
    public ResponseEntity<List<TankLevel>> list() {
        return ResponseEntity.ok(tanks.list());
    }

    /** Suma litros al tanque de {@code supply} en {@code zoneId}; la primera vez también declara su capacidad. */
    @PostMapping("/{zoneId}/{supply}/recarga")
    public ResponseEntity<TankLevel> load(@PathVariable String zoneId, @PathVariable Supply supply,
                                          @RequestBody(required = false) NewTankLoad body,
                                          @RequestHeader(value = "X-Usuario", required = false) String user) {
        return ResponseEntity.ok(tanks.load(zoneId, supply, body, UsuarioSesion.autorDelCambio(user)));
    }

    @ExceptionHandler({InvalidTankLoadException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(Exception ex) {
        String message = ex instanceof InvalidTankLoadException ? ex.getMessage()
                : ex instanceof MethodArgumentTypeMismatchException
                ? "El insumo no existe: usá FERTILIZER o FUNGICIDE."
                : "El cuerpo de la carga no es un JSON válido.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(TankZoneNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(TankZoneNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
