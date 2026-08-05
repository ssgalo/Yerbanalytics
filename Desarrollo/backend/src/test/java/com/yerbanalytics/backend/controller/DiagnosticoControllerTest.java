package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.AlmacenamientoImagenService;
import com.yerbanalytics.backend.service.CapturaService;
import com.yerbanalytics.backend.service.SimulacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de diagnósticos por el camino único.
 *
 * <p>Lo que estos tests protegen no es sólo el happy path: es la propiedad de que el
 * diagnóstico cargado a mano y el que emitirá el modelo sean <em>la misma operación</em>. Si
 * alguien agrega una compuerta por modo, un endpoint de simulación o una marca de origen,
 * estos tests fallan.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosticoControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CapturaService capturaService;
    @Autowired private SimulacionService simulacionService;

    private static final byte[] JPEG = "jpeg-para-diagnostico".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void aislarCanales() {
        capturaService.cerrarCanales();
    }

    /** Deja una captura recibida en el sistema y devuelve su id. */
    private String capturaExistente() throws Exception {
        String codigo = json.readTree(mvc.perform(post("/api/camara/vinculacion"))
                .andReturn().getResponse().getContentAsString()).get("codigo").asText();
        String refresh = json.readTree(mvc.perform(post("/api/camara/v1/enrolar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigo", codigo, "nombre", "diag test"))))
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        String token = json.readTree(mvc.perform(post("/api/camara/v1/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        String ordenId = json.readTree(mvc.perform(post("/api/capturas/ordenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("sectorId", "MZ-2-005", "posicionRiel", 900))))
                .andReturn().getResponse().getContentAsString()).get("ordenId").asText();

        String meta = json.writeValueAsString(Map.of(
                "ancho", 1280, "alto", 720,
                "sha256", AlmacenamientoImagenService.sha256(JPEG),
                "capturadaEn", System.currentTimeMillis()));

        MvcResult r = mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(new MockMultipartFile("imagen", "c.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG))
                        .file(new MockMultipartFile("meta", "m.json",
                                MediaType.APPLICATION_JSON_VALUE, meta.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();

        return json.readTree(r.getResponse().getContentAsString()).get("capturaId").asText();
    }

    private String alta(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void alta_valida_persisteYDevuelveLaImagenDeSuCaptura() throws Exception {
        String capturaId = capturaExistente();

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "capturaId", capturaId,
                                "estado", "Clorosis",
                                "conf", 92.5,
                                "sev", "Alta"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capturaId").value(capturaId))
                .andExpect(jsonPath("$.estado").value("Clorosis"))
                .andExpect(jsonPath("$.conf").value(92.5))
                .andExpect(jsonPath("$.concluyente").value(true))
                .andExpect(jsonPath("$.imagenUrl").value("/api/capturas/" + capturaId + "/imagen"))
                // El sector y la zona se toman de la captura cuando no vienen explícitos.
                .andExpect(jsonPath("$.sectorId").value("MZ-2-005"))
                .andExpect(jsonPath("$.zonaId").value("MZ-2"));
    }

    @Test
    void alta_sinCaptura_devuelve400() throws Exception {
        // Todo diagnóstico nace del análisis de una imagen: sin captura no hay diagnóstico.
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("estado", "Clorosis", "conf", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conCapturaInexistente_devuelve400() throws Exception {
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "capturaId", "CAP-999999", "estado", "Clorosis", "conf", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conSectorInexistente_devuelve400() throws Exception {
        String capturaId = capturaExistente();

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "capturaId", capturaId, "sectorId", "MZ-99-999",
                                "estado", "Clorosis", "conf", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conConfianzaFueraDeRango_devuelve400() throws Exception {
        String capturaId = capturaExistente();

        for (Object conf : new Object[]{-1, 101, 250.5}) {
            Map<String, Object> body = new HashMap<>();
            body.put("capturaId", capturaId);
            body.put("estado", "Clorosis");
            body.put("conf", conf);
            mvc.perform(post("/api/diagnosticos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void alta_conEstadoFueraDeLaTaxonomia_devuelve400() throws Exception {
        String capturaId = capturaExistente();

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "capturaId", capturaId, "estado", "Se ve feo", "conf", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confianzaBajoElUmbral_quedaNoConcluyente() throws Exception {
        String capturaId = capturaExistente();

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "capturaId", capturaId, "estado", "Daño biótico", "conf", 60.0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.concluyente").value(false));
    }

    @Test
    void alta_daElMismoResultadoEnModoEstaticoYEnSimulacion() throws Exception {
        // El modelo de IA no va a consultar el modo de operación antes de emitir un
        // diagnóstico, así que el alta tampoco puede depender de él. Si alguien agrega una
        // compuerta por modo, este test falla.
        SimulacionService.Modo original = simulacionService.getModo();
        try {
            simulacionService.setModo(SimulacionService.Modo.ESTATICO);
            String estatico = alta(Map.of("capturaId", capturaExistente(),
                    "estado", "Estrés solar", "conf", 88.0, "sev", "Media"));

            simulacionService.setModo(SimulacionService.Modo.SIMULACION);
            String simulacion = alta(Map.of("capturaId", capturaExistente(),
                    "estado", "Estrés solar", "conf", 88.0, "sev", "Media"));

            var a = json.readTree(estatico);
            var b = json.readTree(simulacion);

            // Mismos campos, mismos valores: sólo cambian el id y la captura.
            org.junit.jupiter.api.Assertions.assertEquals(a.get("estado"), b.get("estado"));
            org.junit.jupiter.api.Assertions.assertEquals(a.get("conf"), b.get("conf"));
            org.junit.jupiter.api.Assertions.assertEquals(a.get("sev"), b.get("sev"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    a.get("concluyente"), b.get("concluyente"));
            // Y ninguno lleva marca de origen.
            org.junit.jupiter.api.Assertions.assertFalse(a.has("origen"));
            org.junit.jupiter.api.Assertions.assertFalse(b.has("origen"));
        } finally {
            simulacionService.setModo(original);
        }
    }

    @Test
    void elDiagnosticoAparceEnElSnapshotDelVivero() throws Exception {
        String capturaId = capturaExistente();
        String creado = alta(Map.of("capturaId", capturaId,
                "estado", "Clorosis", "conf", 95.0, "sev", "Alta"));
        String id = json.readTree(creado).get("id").asText();

        // Aparece como una tarjeta más, con su imagen real, indistinguible de una que emitiría
        // el modelo. El dashboard no sabe de dónde salió.
        mvc.perform(get("/api/nursery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnoses[?(@.id=='" + id + "')]").exists())
                .andExpect(jsonPath("$.diagnoses[?(@.id=='" + id + "')].imagenUrl")
                        .value("/api/capturas/" + capturaId + "/imagen"))
                .andExpect(jsonPath("$.diagById['" + id + "']").exists());
    }

    @Test
    void losDiagnosticosDerivadosDeSectoresNoTraenImagen() throws Exception {
        // Las cards sintéticas (prefijo DG-) conviven con las persistidas (DX-) sin colisionar,
        // y recurren al gradiente porque no tienen fotografía.
        mvc.perform(get("/api/nursery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnoses[?(@.id =~ /DG-.*/)].imagenUrl").isEmpty());
    }

    @Test
    void listado_devuelveDelMasRecienteAlMasAntiguo() throws Exception {
        alta(Map.of("capturaId", capturaExistente(), "estado", "Sano", "conf", 99.0));

        mvc.perform(get("/api/diagnosticos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].creadoEn").exists());
    }
}
