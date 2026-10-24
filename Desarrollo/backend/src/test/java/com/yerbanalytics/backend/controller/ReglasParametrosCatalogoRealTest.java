package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.yerbanalytics.backend.seguridad.ConTodosLosPermisos;
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
@ConTodosLosPermisos
@DisplayName("GET /api/rules/parametros con el catálogo real")
class ReglasParametrosCatalogoRealTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private List<Rule> reglasRegistradas;

    /** Las reglas de macro-zona (plan de nutrición) figuran en el mismo catálogo que las de sector. */
    @Autowired
    private List<ZoneRule> reglasDeZona;

    /** Quien lee parámetros: las reglas y también lo que ejecuta sin serlo (el despacho de riego). */
    @Autowired
    private List<ConsumidorParametros> consumidores;

    @Autowired
    private CatalogoParametros catalogo;

    /** Todo lo que se puede prender o apagar: reglas por sector, de macro-zona y servicios que deciden. */
    @Autowired
    private DemoControllableRegistry piezas;

    private JsonNode catalogoJson() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(cuerpo);
    }

    @Test
    void devuelveLosParametrosRealesConSuFabrica() throws Exception {
        mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros[?(@.clave=='riego.umbral-humedad' && @.regla=='RiegoPorDeficitRule')].fabrica").value("45"));
    }

    @Test
    void cadaReglaTieneSuCopia_yLoQueElDespachoRevalidaSeEditaSoloEnLaReglaQueDecide() throws Exception {
        JsonNode json = catalogoJson();

        // rediseno-motor-reglas D-1: cada pieza que lee el umbral de riego tiene su copia (la alerta de la falla
        // hidráulica, S-05, también lo lee para saber si pide el riego manual).
        assertThat(copias(json, "riego.umbral-humedad")).containsExactlyInAnyOrder("FueraDeVentanaRiegoRule",
                "PausaTrasAplicacionRule", "PosponerPorLluviaRule", "RiegoPorDeficitRule", "HardwareFaultService");
        assertThat(copias(json, "riego.sectores-simultaneos")).containsExactly("DespachoRiego");
        // D-2: el despacho revalida con la copia de la regla que decidió; no tiene copia propia de esas claves.
        assertThat(copias(json, "riego.lluvia-probabilidad")).containsExactly("PosponerPorLluviaRule");
        assertThat(copias(json, "riego.lluvia-mm")).containsExactly("PosponerPorLluviaRule");
        assertThat(copias(json, "riego.lluvia-ventana")).containsExactly("PosponerPorLluviaRule");
        // F-S1 (la reposición por estrés solar) tiene su propia pausa tras una aplicación.
        assertThat(copias(json, "riego.pausa-tras-aplicacion"))
                .containsExactlyInAnyOrder("PausaTrasAplicacionRule", "SolarStressRule");
        assertThat(copias(json, "riego.exceptuado-bloqueo")).containsExactly("DeficitCriticoRule");
        assertThat(copias(json, "seguridad.antiguedad-max-lectura")).contains("StaleSensorRule")
                .doesNotContain("DespachoRiego");
        assertThat(copias(json, "riego.saturacion-bloqueo")).containsExactly("SustratoSaturadoRule");
        assertThat(copias(json, "riego.ventana-normal"))
                .containsExactlyInAnyOrder("FueraDeVentanaRiegoRule", "SolarStressRule");
        // El intervalo del plan de nutrición se edita en N-01; el servicio del plan lo lee de ahí.
        assertThat(copias(json, "nutricion.intervalo-plan")).containsExactly("NutritionPlanRule");
        // La regla de ciclo lee el umbral crítico para dejar pasar a R-02.
        assertThat(copias(json, "riego.umbral-critico")).contains("CicloLecturaRiegoRule");
        json.get("parametros").forEach(p -> assertThat(p.has("usadoPor")).isFalse());
    }

    @Test
    void elSeguimientoViejoNoEstaEnElCatalogo_laEfectividadUsaEfectividadPunto() throws Exception {
        List<String> claves = new ArrayList<>();
        catalogoJson().get("parametros").forEach(p -> claves.add(p.get("clave").asText()));

        assertThat(claves).noneMatch(c -> c.startsWith("seguimiento."));
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

        assertThat(cadena).containsExactly("SustratoSaturadoRule", "CicloLecturaRiegoRule", "DeficitCriticoRule", "IneffectiveActionRule",
                "FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule", "PosponerPorLluviaRule", "RiegoPorDeficitRule",
                "SolarStressRule");
        for (String id : cadena) {
            String etiqueta = nodos.get(id).get("label").asText();
            assertThat(etiqueta).as(id).isNotBlank().isNotEqualTo(id).doesNotContain("Rule");
        }
        // Cada regla de la cadena figura una sola vez y con sus claves.
        assertThat(nodos.get("DeficitCriticoRule").get("parametros").toString()).contains("riego.exceptuado-bloqueo");
        assertThat(nodos.get("CicloLecturaRiegoRule").get("parametros").toString()).contains("riego.umbral-critico");
        assertThat(nodos).doesNotContainKeys("IrrigationRule", "WeatherOverrideRule", "DailyVolumeLimitRule");
    }

    /** Las piezas que tienen copia de la clave. */
    private static List<String> copias(JsonNode json, String clave) {
        List<String> out = new ArrayList<>();
        json.get("parametros").forEach(p -> {
            if (p.get("clave").asText().equals(clave)) {
                out.add(p.get("regla").asText());
            }
        });
        return out;
    }

    @Test
    void invariantesEstructurales_unaEntradaPorCopia_reglasExistentesYCopiasCoherentes() throws Exception {
        JsonNode json = catalogoJson();

        // Una entrada por (pieza, clave): exactamente lo que cada consumidor declara como propio.
        List<String> enJsonCopias = new ArrayList<>();
        json.get("parametros").forEach(p -> enJsonCopias.add(p.get("regla").asText() + "|" + p.get("clave").asText()));
        List<String> declaradas = new ArrayList<>();
        consumidores.forEach(c -> c.parametros().forEach(d -> declaradas.add(c.name() + "|" + d.clave())));
        assertThat(enJsonCopias).isNotEmpty().doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(declaradas);
        List<String> clavesDelCatalogo = catalogo.definiciones().stream().map(DefinicionParametro::clave).toList();
        json.get("parametros").forEach(p -> assertThat(clavesDelCatalogo).contains(p.get("clave").asText()));

        // Toda pieza conmutable figura, una vez, con exactamente los parámetros que declara (ninguno si no lee).
        Map<String, List<String>> declarado = new LinkedHashMap<>();
        piezas.all().forEach(p -> declarado.put(p.name(), p instanceof ConsumidorParametros c
                ? c.parametros().stream().map(DefinicionParametro::clave).toList() : List.of()));
        assertThat(declarado.keySet()).containsAll(reglasRegistradas.stream().map(Rule::name).toList())
                .containsAll(reglasDeZona.stream().map(ZoneRule::name).toList());
        Map<String, List<String>> enJson = new LinkedHashMap<>();
        json.get("reglas").forEach(r -> {
            List<String> ps = new ArrayList<>();
            r.get("parametros").forEach(x -> ps.add(x.asText()));
            enJson.put(r.get("id").asText(), ps);
            assertThat(r.get("etiquetaFabrica").asText()).isNotBlank();
            assertThat(r.has("eliminada")).isTrue();
        });
        assertThat(enJson).containsOnlyKeys(declarado.keySet().toArray(String[]::new));
        declarado.forEach((regla, ps) -> assertThat(enJson.get(regla)).as(regla).containsExactlyElementsOf(ps));

        // Lo que una pieza lee de otra (D-2) nombra a una dueña que lo declara como propio.
        Map<String, ConsumidorParametros> porNombre = new LinkedHashMap<>();
        consumidores.forEach(c -> porNombre.put(c.name(), c));
        consumidores.forEach(c -> c.parametrosDe().forEach((def, duena) -> {
            assertThat(porNombre).as(c.name() + " lee " + def.clave()).containsKey(duena);
            assertThat(porNombre.get(duena).parametros()).contains(def);
            assertThat(c.parametros()).doesNotContain(def);
        }));
    }

    private JsonNode regla(JsonNode json, String id) {
        for (JsonNode r : json.get("reglas")) {
            if (r.get("id").asText().equals(id)) {
                return r;
            }
        }
        throw new AssertionError("no está la regla " + id);
    }

    @Test
    @DisplayName("lista todas las piezas conmutables, también los servicios, con lo que la pantalla necesita")
    void listaTodasLasPiezasConSuDeclaracion() throws Exception {
        JsonNode json = catalogoJson();

        assertThat(json.get("reglas")).hasSize(piezas.all().size());
        assertThat(piezas.all().stream().map(com.yerbanalytics.backend.engine.overrides.DemoControllable::name))
                .contains("HardwareFaultService", "ApplicationPreconditions", "HorarioMediasombraService");

        JsonNode r01 = regla(json, "RiegoPorDeficitRule");
        assertThat(r01.get("label").asText()).isEqualTo("Riego por déficit hídrico");
        assertThat(r01.get("codigo").asText()).isEqualTo("R-01");
        assertThat(r01.get("rama").asText()).isEqualTo("RIEGO");
        assertThat(r01.get("objetivo").asText()).isEqualTo("IRRIGATION");
        assertThat(r01.get("efecto").asText()).isEqualTo("ACTS");
        assertThat(r01.get("activa").asBoolean()).isTrue();
        assertThat(r01.get("activaPorDefecto").asBoolean()).isTrue();
        assertThat(r01.has("apagadaPorDemo")).isFalse();
        assertThat(r01.get("proteccion").isNull()).isTrue();
        assertThat(r01.get("estado").isNull()).isTrue();

        JsonNode r04 = regla(json, "SustratoSaturadoRule");
        assertThat(r04.get("efecto").asText()).isEqualTo("PROTECTS");
        assertThat(r04.get("proteccion").asText()).isEqualTo("Se riega con el sustrato saturado.");

        JsonNode retirada = regla(json, "SupplyRule");
        assertThat(retirada.get("activa").asBoolean()).isFalse();
        assertThat(retirada.get("activaPorDefecto").asBoolean()).isFalse();

        assertThat(regla(json, "ManualLockRule").get("objetivo").asText()).isEqualTo("PROTECTION");
        assertThat(regla(json, "HardwareFaultService").get("efecto").asText()).isEqualTo("PROTECTS");
        assertThat(regla(json, "EffectivenessService").get("efecto").asText()).isEqualTo("WARNS");
    }
}
