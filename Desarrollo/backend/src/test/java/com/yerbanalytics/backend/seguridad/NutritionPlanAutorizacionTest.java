package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cambiar el intervalo del plan de nutrición de una macro-zona es configuración agronómica: exige {@code configuracion.editar}. */
class NutritionPlanAutorizacionTest extends SeguridadIntegracionBase {

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
        mvc.perform(put("/api/zonas/MZ-1/plan-nutricion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalDays\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elOperarioNoEditaLaConfiguracionAgronomica_403() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        mvc.perform(put("/api/zonas/MZ-1/plan-nutricion").cookie(operario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("configuracion.editar"));
    }

    @Test
    void elIngenieroAgronomoPuedeFijarElIntervalo() throws Exception {
        Cookie ingeniero = loginConMatrizPorDefecto(Rol.INGENIERO_AGRONOMO);

        // Una macro-zona que no existe: pasa la autorización y responde 404 (no 403).
        mvc.perform(put("/api/zonas/MZ-99/plan-nutricion").cookie(ingeniero)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":7}"))
                .andExpect(status().isNotFound());
    }
}
