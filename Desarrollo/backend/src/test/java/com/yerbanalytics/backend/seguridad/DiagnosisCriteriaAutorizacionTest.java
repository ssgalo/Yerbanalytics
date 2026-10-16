package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permisos y contrato de {@code GET/PUT /api/configuracion/diagnostico} (criterio-diagnostico).
 * Corre en una transacción que se revierte: el criterio de la base no cambia.
 */
class DiagnosisCriteriaAutorizacionTest extends SeguridadIntegracionBase {

    private static final String RUTA = "/api/configuracion/diagnostico";

    private JsonNode leer(Cookie c) throws Exception {
        return cuerpo(mvc.perform(get(RUTA).cookie(c)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void laCuentaDeServicioLeeElCriterio() throws Exception {
        Cookie servicio = loginComo(Rol.SERVICIO);

        mvc.perform(get(RUTA).cookie(servicio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severityBands.length()").value(4))
                .andExpect(jsonPath("$.severityBands[0].state").value("Clorosis"))
                .andExpect(jsonPath("$.severityBands[1].state").value("Estrés solar"))
                .andExpect(jsonPath("$.severityBands[2].state").value("Plaga foliar"))
                .andExpect(jsonPath("$.severityBands[3].state").value("Daño fúngico"))
                .andExpect(jsonPath("$.severityBands[0].minimum").isNumber())
                .andExpect(jsonPath("$.severityBands[0].mediumFrom").isNumber())
                .andExpect(jsonPath("$.severityBands[0].highFrom").isNumber())
                .andExpect(jsonPath("$.criticality.length()").value(4));
    }

    @Test
    void sinConfiguracionEditar_403YElCriterioNoCambia() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);
        JsonNode antes = leer(operario);
        ObjectNode cambiado = antes.deepCopy();
        ((ObjectNode) cambiado.get("severityBands").get(0)).put("minimum", 1);

        mvc.perform(put(RUTA).cookie(operario).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cambiado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("configuracion.editar"));

        assertThat(leer(operario)).isEqualTo(antes);
    }

    @Test
    void elIngenieroGuardaYRecibeElCriterioGuardado() throws Exception {
        Cookie ingeniero = loginComo(Rol.INGENIERO_AGRONOMO);
        ObjectNode criterio = leer(ingeniero).deepCopy();
        ObjectNode fungico = (ObjectNode) criterio.get("severityBands").get(3);
        fungico.put("minimum", 2).put("mediumFrom", 3).put("highFrom", 8);
        criterio.putArray("criticality").add("Daño fúngico").add("Clorosis").add("Plaga foliar").add("Estrés solar");

        mvc.perform(put(RUTA).cookie(ingeniero).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(criterio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severityBands[3].mediumFrom").value(3))
                .andExpect(jsonPath("$.criticality[1]").value("Clorosis"));
    }

    @Test
    void criterioInvalido_400ConMensajeYNoCambia() throws Exception {
        Cookie ingeniero = loginComo(Rol.INGENIERO_AGRONOMO);
        JsonNode antes = leer(ingeniero);
        ObjectNode invalido = antes.deepCopy();
        ((ObjectNode) invalido.get("severityBands").get(0)).put("minimum", 10).put("mediumFrom", 5);

        mvc.perform(put(RUTA).cookie(ingeniero).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        assertThat(leer(ingeniero)).isEqualTo(antes);
    }

    @Test
    void sinDiagnosticosVer_noLee() throws Exception {
        // Ningún rol fijo carece de diagnosticos.ver, así que se prueba sin sesión.
        mvc.perform(get(RUTA)).andExpect(result -> {
            int s = result.getResponse().getStatus();
            assertThat(s).isIn(401, 403);
        });
    }
}
