package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ver bloqueos exige {@code bloqueos.ver}; bloquear y reanudar, {@code bloqueos.gestionar}. */
class BloqueoAutorizacionTest extends SeguridadIntegracionBase {

    /**
     * La matriz es de la base y se siembra sólo si está vacía: una base anterior a este cambio no tiene los permisos de
     * bloqueo (ver {@code migracion-permisos-bloqueo.sql}). El test fija la matriz por defecto del rol, dentro de su
     * transacción, para probar la autorización y no el estado de la base de desarrollo.
     */
    private Cookie loginConMatrizPorDefecto(Rol rol) throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        List<String> codigos = Permiso.matrizPorDefecto(rol).stream().map(Permiso::getCodigo).sorted().toList();
        mvc.perform(put("/api/roles/" + rol.name() + "/permisos").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("permisos", codigos))))
                .andExpect(status().isOk());
        return loginComo(rol);
    }

    @Test
    void sinSesionNoSeVeNada_401() throws Exception {
        mvc.perform(get("/api/bloqueos")).andExpect(status().isUnauthorized());
    }

    @Test
    void elProductorViveristaVeLosBloqueosPeroNoLosGestiona() throws Exception {
        Cookie productor = loginConMatrizPorDefecto(Rol.PRODUCTOR_VIVERISTA);

        mvc.perform(get("/api/bloqueos").cookie(productor)).andExpect(status().isOk());
        mvc.perform(post("/api/bloqueos").cookie(productor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zonaId\":\"MZ-1\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("bloqueos.gestionar"));
        mvc.perform(delete("/api/bloqueos/1").cookie(productor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("bloqueos.gestionar"));
    }

    @Test
    void elOperarioPuedeGestionarLosBloqueos() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        mvc.perform(get("/api/bloqueos").cookie(operario)).andExpect(status().isOk());
        // Sin zona: pasa la autorización y la validación responde 400 (no 403).
        mvc.perform(post("/api/bloqueos").cookie(operario).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
