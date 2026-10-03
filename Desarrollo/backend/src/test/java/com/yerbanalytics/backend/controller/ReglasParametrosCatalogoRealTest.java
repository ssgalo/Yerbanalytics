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
                .andExpect(jsonPath("$.parametros[?(@.clave=='riego.umbral-humedad')].fabrica").value("45"));
    }

    @Test
    void elUmbralDeRiegoLoCompartenLasCuatroReglasQueLoUsanYElSimultaneoLoUsaElDespacho() throws Exception {
        JsonNode json = catalogoJson();

        List<String> umbral = usadoPor(json, "riego.umbral-humedad");
        assertThat(umbral).containsExactlyInAnyOrder("FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule",
                "PosponerPorLluviaRule", "RiegoPorDeficitRule");
        assertThat(usadoPor(json, "riego.sectores-simultaneos")).containsExactly("DespachoRiego");
        assertThat(usadoPor(json, "riego.lluvia-probabilidad")).containsExactlyInAnyOrder("PosponerPorLluviaRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.lluvia-mm")).containsExactlyInAnyOrder("PosponerPorLluviaRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.lluvia-ventana")).containsExactlyInAnyOrder("PosponerPorLluviaRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.pausa-tras-aplicacion")).containsExactlyInAnyOrder("PausaTrasAplicacionRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.exceptuado-bloqueo")).containsExactlyInAnyOrder("DeficitCriticoRule", "DespachoRiego");
        // Lo que el despacho revalida antes de abrir cada válvula, junto a las reglas que ya lo decidieron.
        assertThat(usadoPor(json, "seguridad.antiguedad-max-lectura")).contains("StaleSensorRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.saturacion-bloqueo")).containsExactlyInAnyOrder("SustratoSaturadoRule", "DespachoRiego");
        assertThat(usadoPor(json, "riego.ventana-normal")).containsExactlyInAnyOrder("FueraDeVentanaRiegoRule", "DespachoRiego");
        // La regla de ciclo lee el umbral crítico para dejar pasar a R-02.
        assertThat(usadoPor(json, "riego.umbral-critico")).contains("CicloLecturaRiegoRule");
    }

    @Test
    void lasClavesDeLasReglasViejasNoEstanEnElCatalogo() throws Exception {
        List<String> claves = new ArrayList<>();
        catalogoJson().get("parametros").forEach(p -> claves.add(p.get("clave").asText()));

        assertThat(claves).doesNotContain("riego.tiempo-max-apertura", "riego.max-riegos-24h", "riego.max-riegos-24h-sector");
    }

    @Test
    void laRamaRiegoDelDagVaEnOrdenYConEtiquetasLegibles() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/rules/schema")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode dag = new ObjectMapper().readTree(cuerpo);

        Map<String, JsonNode> nodos = new LinkedHashMap<>();
        dag.get("nodes").forEach(n -> nodos.put(n.get("id").asText(), n));
        // Se sigue la cadena "Continúa" de la rama RIEGO desde el último nodo global.
        List<String> cadena = new ArrayList<>();
        String actual = "StaleSensorRule";
        while (true) {
            String origen = actual;
            String siguiente = null;
            for (JsonNode e : dag.get("edges")) {
                String destino = e.get("target").asText();
                if (e.get("source").asText().equals(origen) && "Continúa".equals(e.get("label").asText())
                        && nodos.containsKey(destino)
                        && ("RIEGO".equals(nodos.get(destino).get("branch").asText()))) {
                    siguiente = destino;
                }
            }
            if (siguiente == null || siguiente.startsWith("success-")) {
                break;
            }
            cadena.add(siguiente);
            actual = siguiente;
        }

        assertThat(cadena).containsExactly("SustratoSaturadoRule", "CicloLecturaRiegoRule", "DeficitCriticoRule",
                "FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule", "PosponerPorLluviaRule", "RiegoPorDeficitRule");
        for (String id : cadena) {
            String etiqueta = nodos.get(id).get("label").asText();
            assertThat(etiqueta).as(id).isNotBlank().isNotEqualTo(id).doesNotContain("Rule");
        }
        // Cada regla de la cadena figura una sola vez y con sus claves.
        assertThat(nodos.get("DeficitCriticoRule").get("parametros").toString()).contains("riego.exceptuado-bloqueo");
        assertThat(nodos.get("CicloLecturaRiegoRule").get("parametros").toString()).contains("riego.umbral-critico");
        assertThat(nodos).doesNotContainKeys("IrrigationRule", "WeatherOverrideRule", "DailyVolumeLimitRule");
    }

    private static List<String> usadoPor(JsonNode json, String clave) {
        List<String> out = new ArrayList<>();
        json.get("parametros").forEach(p -> {
            if (p.get("clave").asText().equals(clave)) {
                p.get("usadoPor").forEach(x -> out.add(x.asText()));
            }
        });
        return out;
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
