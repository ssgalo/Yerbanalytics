package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ver los tanques exige {@code insumos.ver}; registrar una carga, {@code insumos.gestionar}. */
class InsumoAutorizacionTest extends SeguridadIntegracionBase {

    /** Fija la matriz por defecto del rol (la de la base de desarrollo puede ser anterior a los permisos de insumos). */
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
        mvc.perform(get("/api/tanques")).andExpect(status().isUnauthorized());
    }

    @Test
    void elProductorViveristaVeLosTanquesPeroNoRegistraCargas() throws Exception {
        Cookie productor = loginConMatrizPorDefecto(Rol.PRODUCTOR_VIVERISTA);

        mvc.perform(get("/api/tanques").cookie(productor)).andExpect(status().isOk());
        mvc.perform(post("/api/tanques/MZ-1/FERTILIZER/recarga").cookie(productor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liters\":1,\"capacityLiters\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("insumos.gestionar"));
    }

    @Test
    void elOperarioPuedeRegistrarCargas() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        mvc.perform(get("/api/tanques").cookie(operario)).andExpect(status().isOk());
        // Una macro-zona que no existe: pasa la autorización y la carga responde 404 (no 403).
        mvc.perform(post("/api/tanques/MZ-99/FERTILIZER/recarga").cookie(operario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liters\":1,\"capacityLiters\":5}"))
                .andExpect(status().isNotFound());
    }
}
