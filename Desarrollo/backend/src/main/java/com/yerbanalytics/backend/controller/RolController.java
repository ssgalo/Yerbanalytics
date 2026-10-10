package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.seguridad.Permiso;
import com.yerbanalytics.backend.seguridad.Rol;
import com.yerbanalytics.backend.seguridad.RolPermisoService;
import com.yerbanalytics.backend.seguridad.SeguridadExceptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Catálogo de permisos y matriz rol → permisos (HU-20 CA-01). Exige {@code usuarios.gestionar}. */
@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolPermisoService service;

    public RolController(RolPermisoService service) {
        this.service = service;
    }

    public record PermisoCatalogo(String codigo, String grupo, String descripcion, String lectura) {}

    /** En el orden del enum, que ya viene agrupado. */
    @GetMapping("/permisos")
    public List<PermisoCatalogo> catalogo() {
        return Arrays.stream(Permiso.values())
                .map(p -> new PermisoCatalogo(p.getCodigo(), p.getGrupo(), p.getDescripcion(),
                        p.getLectura() != null ? p.getLectura().getCodigo() : null))
                .toList();
    }

    @GetMapping
    public List<RolPermisoService.RolPermisos> matriz() {
        return service.matriz();
    }

    public record PermisosRequest(List<String> permisos) {}

    @PutMapping("/{rol}/permisos")
    public RolPermisoService.RolPermisos guardar(@PathVariable String rol, @RequestBody PermisosRequest body) {
        Rol r;
        try {
            r = Rol.valueOf(rol);
        } catch (IllegalArgumentException e) {
            throw new SeguridadExceptions.Invalida("El rol '" + rol + "' no existe.");
        }
        return service.guardar(r, body.permisos());
    }
}
