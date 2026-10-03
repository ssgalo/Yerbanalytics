package com.yerbanalytics.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranque real: el catálogo se construye con las familias reales y las 9 reglas registradas
 * (si algo no cerrara, el contexto no levanta). Sólo lectura: no ensucia la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GET /api/rules/parametros con el catálogo real")
class ReglasParametrosCatalogoRealTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void devuelveLosParametrosRealesConSuFabrica() throws Exception {
        mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.length()").value(11))
                .andExpect(jsonPath("$.parametros[?(@.clave=='riego.umbral-humedad')].fabrica").value("42"))
                .andExpect(jsonPath("$.reglas.length()").value(9));
    }
}
