package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Dispositivo;
import com.yerbanalytics.backend.dto.HardwareData;
import com.yerbanalytics.backend.dto.RepairRequest;
import com.yerbanalytics.backend.exception.HardwareConflictException;
import com.yerbanalytics.backend.exception.InvalidHardwareException;
import com.yerbanalytics.backend.seguridad.UsuarioSesion;
import com.yerbanalytics.backend.service.HardwareFaultService;
import com.yerbanalytics.backend.service.HardwareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Estado técnico y registro de hardware (HU-18 / HU-21). El alta valida la unicidad del
 * serial/MAC y del actuador por sector: un conflicto responde 409 y un dato inválido 400. La reparación de un
 * dispositivo averiado (S-05) la firma el usuario de la sesión.
 */
@RestController
@RequestMapping("/api/hardware")
public class HardwareController {

    private final HardwareService hardwareService;
    private final HardwareFaultService faultService;

    public HardwareController(HardwareService hardwareService, HardwareFaultService faultService) {
        this.hardwareService = hardwareService;
        this.faultService = faultService;
    }

    @GetMapping
    public ResponseEntity<HardwareData> getHardware() {
        return ResponseEntity.ok(hardwareService.getHardware());
    }

    @PostMapping
    public ResponseEntity<HardwareData> registrar(@RequestBody Dispositivo dto) {
        return ResponseEntity.ok(hardwareService.registrarDispositivo(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HardwareData> recambiar(@PathVariable String id, @RequestBody Dispositivo dto) {
        return ResponseEntity.ok(hardwareService.recambiarDispositivo(id, dto));
    }

    /**
     * Registers the repair of a faulty device (S-05, HU-21 CA-04): clears the fault mark, leaves the event in the
     * history and returns the device to autonomous control. Who repairs comes from the session, not from the body.
     */
    @PostMapping("/{id}/reparacion")
    public ResponseEntity<HardwareData> registrarReparacion(
            @PathVariable String id,
            @RequestBody(required = false) RepairRequest body,
            @RequestHeader(value = "X-Usuario", required = false) String user) {
        faultService.repair(id, body != null ? body.detalle() : null, UsuarioSesion.autorDelCambio(user));
        return ResponseEntity.ok(hardwareService.getHardware());
    }

    /** Missing or invalid data on hardware registration/replacement (HU-18 CA-02) → 400. */
    @ExceptionHandler(InvalidHardwareException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(InvalidHardwareException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** Uniqueness conflict: duplicate serial/MAC or actuator (HU-18 CA-03) → 409. */
    @ExceptionHandler(HardwareConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflicto(HardwareConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
