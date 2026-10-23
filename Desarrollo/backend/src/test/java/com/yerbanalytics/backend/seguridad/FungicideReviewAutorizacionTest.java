package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registrar la revisión de un sector con el tope de fitosanitarios alcanzado (F-06) exige {@code insumos.gestionar}. */
class FungicideReviewAutorizacionTest extends SeguridadIntegracionBase {

    private static final String REVIEW = "{\"detail\":\"Revisé el sector\"}";

    /** Fija la matriz por defecto del rol (la de la base de desarrollo puede ser anterior a los permisos actuales). */
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
    void sinSesion_401() throws Exception {
        mvc.perform(post("/api/sectores/MZ-1-001/revision-fitosanitario").contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elProductorViveristaNoRegistraRevisiones_403() throws Exception {
        Cookie productor = loginConMatrizPorDefecto(Rol.PRODUCTOR_VIVERISTA);

        mvc.perform(post("/api/sectores/MZ-1-001/revision-fitosanitario").cookie(productor)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("insumos.gestionar"));
    }

    @Test
    void elOperarioPuedeRegistrarLaRevision() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        // Un sector que no existe: pasa la autorización y responde 404 (no 403).
        mvc.perform(post("/api/sectores/MZ-99-999/revision-fitosanitario").cookie(operario)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isNotFound());
    }
}
