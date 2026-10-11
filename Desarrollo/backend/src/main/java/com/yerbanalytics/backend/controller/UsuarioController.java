package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.seguridad.UsuarioService;
import com.yerbanalytics.backend.seguridad.UsuarioService.UsuarioDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ABM de usuarios (HU-20 CA-01/CA-02). Exige {@code usuarios.gestionar} (ver {@code MapaPermisos}). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioDto> listar(@RequestParam(defaultValue = "false") boolean incluirBajas) {
        return service.listar(incluirBajas);
    }

    public record AltaRequest(String username, String nombre, String rol, String clave) {}

    @PostMapping
    public ResponseEntity<UsuarioDto> alta(@RequestBody AltaRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.alta(body.username(), body.nombre(), body.rol(), body.clave()));
    }

    public record EdicionRequest(String nombre, String rol) {}

    @PutMapping("/{id}")
    public UsuarioDto editar(@PathVariable Long id, @RequestBody EdicionRequest body) {
        return service.editar(id, body.nombre(), body.rol());
    }

    @PostMapping("/{id}/suspender")
    public UsuarioDto suspender(@PathVariable Long id) {
        return service.suspender(id);
    }

    @PostMapping("/{id}/reactivar")
    public UsuarioDto reactivar(@PathVariable Long id) {
        return service.reactivar(id);
    }

    @PostMapping("/{id}/baja")
    public UsuarioDto baja(@PathVariable Long id) {
        return service.baja(id);
    }

    public record BlanqueoRequest(String clave) {}

    @PutMapping("/{id}/clave")
    public ResponseEntity<Void> blanquear(@PathVariable Long id, @RequestBody BlanqueoRequest body) {
        service.blanquearClave(id, body.clave());
        return ResponseEntity.noContent().build();
    }
}
