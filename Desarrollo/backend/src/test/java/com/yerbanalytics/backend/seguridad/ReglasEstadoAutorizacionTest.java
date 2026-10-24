package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prender o apagar una regla exige {@code reglas.editar}, el mismo permiso que editar los parámetros. El simulador (rol
 * Servicio) no lo tiene: su interruptor es el de demo, en memoria, y nunca escribe el estado operativo.
 */
class ReglasEstadoAutorizacionTest extends SeguridadIntegracionBase {

    private static final String BODY = "{\"activa\":false}";

    /** Fija la matriz por defecto del rol dentro de la transacción del test: se prueba la autorización, no la base. */
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
        mvc.perform(put("/api/rules/ShadingRule/estado").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elOperarioNoPuedeCambiarElEstadoDeUnaRegla_403() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        mvc.perform(put("/api/rules/RiegoPorDeficitRule/estado").cookie(operario)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
    }

    @Test
    void elServicioDelSimuladorNoEscribeElEstadoOperativo_403() throws Exception {
        Cookie servicio = loginConMatrizPorDefecto(Rol.SERVICIO);

        mvc.perform(put("/api/rules/RiegoPorDeficitRule/estado").cookie(servicio)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
    }

    @Test
    void elIngenieroAgronomoPuedeApagarUnaRegla_200() throws Exception {
        Cookie ingeniero = loginConMatrizPorDefecto(Rol.INGENIERO_AGRONOMO);

        mvc.perform(put("/api/rules/ShadingRule/estado").cookie(ingeniero)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ShadingRule"))
                .andExpect(jsonPath("$.activa").value(false));
    }

    // ------------------------------------------------------------- nombre propio, eliminar y restaurar (D-5)

    @Test
    void renombrarEliminarYRestaurarSinSesion_401() throws Exception {
        mvc.perform(put("/api/rules/ShadingRule/etiqueta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"etiqueta\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/rules/ShadingRule")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rules/ShadingRule/restaurar")).andExpect(status().isUnauthorized());
    }

    @Test
    void elOperarioNoRenombraNiEliminaNiRestaura_403() throws Exception {
        Cookie operario = loginConMatrizPorDefecto(Rol.OPERARIO);

        mvc.perform(put("/api/rules/RiegoPorDeficitRule/etiqueta").cookie(operario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"etiqueta\":\"Riego normal\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
        mvc.perform(delete("/api/rules/RiegoPorDeficitRule").cookie(operario))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
        mvc.perform(post("/api/rules/RiegoPorDeficitRule/restaurar").cookie(operario))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("reglas.editar"));
    }

    @Test
    void elIngenieroAgronomoRenombraEliminaYRestaura_200() throws Exception {
        Cookie ingeniero = loginConMatrizPorDefecto(Rol.INGENIERO_AGRONOMO);

        mvc.perform(put("/api/rules/FueraDeVentanaRiegoRule/etiqueta").cookie(ingeniero)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"etiqueta\":\"Riego en horario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Riego en horario"));
        mvc.perform(delete("/api/rules/FueraDeVentanaRiegoRule").cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eliminada").value(true))
                .andExpect(jsonPath("$.activa").value(false));
        // El estado operativo se cachea al confirmar la transacción, y la del test no confirma: acá sólo se prueba el
        // permiso (la semántica de restaurar está en ReglasNombreEliminacionControllerTest).
        mvc.perform(post("/api/rules/FueraDeVentanaRiegoRule/restaurar").cookie(ingeniero))
                .andExpect(status().isOk());
    }
}
