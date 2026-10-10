package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerbanalytics.backend.service.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aplicación de permisos en el backend (control-acceso-roles, camara-dispositivos). */
class AutorizacionTest extends SeguridadIntegracionBase {

    @Autowired private TokenService tokenService;

    @Test
    void operarioNoEditaParametros_403ConElPermisoFaltante() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);

        mvc.perform(put("/api/rules/parametros").cookie(operario).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cambios\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
    }

    @Test
    void ingenieroAgronomoEditaParametros() throws Exception {
        Cookie ingeniero = loginComo(Rol.INGENIERO_AGRONOMO);
        JsonNode catalogo = json.readTree(mvc.perform(get("/api/rules/parametros").cookie(ingeniero))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode p = catalogo.get("parametros").get(0);

        mvc.perform(put("/api/rules/parametros").cookie(ingeniero).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("cambios",
                                List.of(Map.of("clave", p.get("clave").asText(), "valor", p.get("valor").asText()))))))
                .andExpect(status().isOk());
    }

    @Test
    void operarioNoDisparaPasadas() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);
        mvc.perform(post("/api/pasadas").cookie(operario).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("pasadas.operar"));
        mvc.perform(get("/api/pasadas/actual").cookie(operario))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("El Operario debería poder ver la pasada, respondió " + s);
                    }
                });
    }

    @Test
    void gestionDeUsuariosYAuditoria_soloAdministrador() throws Exception {
        Cookie ingeniero = loginComo(Rol.INGENIERO_AGRONOMO);
        Cookie operario = loginComo(Rol.OPERARIO);
        Cookie admin = loginComo(Rol.ADMINISTRADOR);

        mvc.perform(get("/api/usuarios").cookie(ingeniero)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").cookie(operario)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void sinCamaraGestionarNoRevocaDispositivos() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);
        mvc.perform(delete("/api/camara/dispositivos/cualquiera").cookie(operario))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("camara.gestionar"));
    }

    @Test
    void laPlataformaDeCapturaExigeSesion() throws Exception {
        mvc.perform(post("/api/camara/vinculacion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/capturas/cualquiera/imagen")).andExpect(status().isUnauthorized());
    }

    @Test
    void lasCredencialesNoSeCruzan() throws Exception {
        // Token de dispositivo contra la API de plataforma: no es una sesión.
        String token = tokenService.emitir("dispositivo-de-prueba");
        mvc.perform(get("/api/nursery").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SIN_SESION"));

        // Sesión de usuario contra el contrato del dispositivo: no es un token.
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/camara/v1/config").cookie(admin)).andExpect(status().isUnauthorized());
    }

    @Test
    void elContratoDeCamaraNoPideSesion() throws Exception {
        String token = tokenService.emitir("dispositivo-de-prueba");
        mvc.perform(get("/api/camara/v1/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void rutaSinPermisoDeclarado_403ParaTodos() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/ruta-que-nadie-declaro").cookie(admin)).andExpect(status().isForbidden());
    }

    @Test
    void elPreflightDeCorsNoPideSesion() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/nursery")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void un401LlevaLosHeadersDeCorsParaQueElDashboardLeaElMotivo() throws Exception {
        mvc.perform(get("/api/nursery").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void laImagenDeUnaCapturaConservaSuCacheControl_noElNoStoreDeSpring() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);
        mvc.perform(get("/api/capturas/no-existe/imagen").cookie(operario))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL));
    }
}
