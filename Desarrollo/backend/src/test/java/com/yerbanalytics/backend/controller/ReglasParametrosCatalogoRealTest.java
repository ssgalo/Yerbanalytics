package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private List<Rule> reglasRegistradas;

    /** Quien lee parámetros: las reglas y también lo que ejecuta sin serlo (el despacho de riego). */
    @Autowired
    private List<ConsumidorParametros> consumidores;

    @Autowired
    private CatalogoParametros catalogo;

    private JsonNode catalogoJson() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(cuerpo);
    }

    @Test
    void devuelveLosParametrosRealesConSuFabrica() throws Exception {
        mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros[?(@.clave=='riego.umbral-humedad')].fabrica").value("42"));
    }

    @Test
    void invariantesEstructurales_clavesUnicas_reglasExistentesYUsadoPorCoherente() throws Exception {
        JsonNode json = catalogoJson();

        List<String> claves = new ArrayList<>();
        json.get("parametros").forEach(p -> claves.add(p.get("clave").asText()));
        assertThat(claves).isNotEmpty().doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(catalogo.definiciones().stream().map(DefinicionParametro::clave).toList());

        // Toda regla registrada figura, una vez, con exactamente los parámetros que declara.
        Map<String, List<String>> declarado = new LinkedHashMap<>();
        reglasRegistradas.forEach(r -> declarado.put(r.name(),
                r.parametros().stream().map(DefinicionParametro::clave).toList()));
        Map<String, List<String>> enJson = new LinkedHashMap<>();
        json.get("reglas").forEach(r -> {
            List<String> ps = new ArrayList<>();
            r.get("parametros").forEach(x -> ps.add(x.asText()));
            enJson.put(r.get("id").asText(), ps);
        });
        assertThat(enJson).containsOnlyKeys(declarado.keySet().toArray(String[]::new));
        declarado.forEach((regla, ps) -> assertThat(enJson.get(regla)).as(regla).containsExactlyElementsOf(ps));

        // Todo usadoPor nombra consumidores (reglas o no) que existen y que de verdad declaran ese parámetro.
        Map<String, List<String>> declaradoPorConsumidores = new LinkedHashMap<>();
        consumidores.forEach(c -> declaradoPorConsumidores.put(c.name(),
                c.parametros().stream().map(DefinicionParametro::clave).toList()));
        json.get("parametros").forEach(p -> {
            String clave = p.get("clave").asText();
            List<String> usadoPor = new ArrayList<>();
            p.get("usadoPor").forEach(x -> usadoPor.add(x.asText()));
            assertThat(usadoPor).as(clave).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(declaradoPorConsumidores.entrySet().stream()
                            .filter(e -> e.getValue().contains(clave)).map(Map.Entry::getKey).toList());
        });
    }
}
