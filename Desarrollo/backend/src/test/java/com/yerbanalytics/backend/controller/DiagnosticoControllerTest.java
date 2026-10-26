package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.AlmacenamientoImagenService;
import com.yerbanalytics.backend.service.CapturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.yerbanalytics.backend.seguridad.ConTodosLosPermisos;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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
@ConTodosLosPermisos
class DiagnosticoControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CapturaService capturaService;
    @Autowired private JdbcTemplate jdbc;

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

    /** Alta anómala completa, con el contrato de design D7. */
    private static Map<String, Object> anomalo(String capturaId, String estado, double conf) {
        Map<String, Object> body = new HashMap<>();
        body.put("capturaId", capturaId);
        body.put("estado", estado);
        body.put("sev", "Media");
        body.put("confidence", "Media");
        body.put("conf", conf);
        return body;
    }

    private static Map<String, Object> deteccion(String label, double score, double... box) {
        return Map.of("label", label, "score", score,
                "box", java.util.Arrays.stream(box).boxed().toList());
    }

    @Test
    void alta_valida_persisteYDevuelveLaImagenDeSuCaptura() throws Exception {
        String capturaId = capturaExistente();
        Map<String, Object> body = anomalo(capturaId, "Clorosis", 62.0);
        body.put("sev", "Alta");
        body.put("detections", List.of(
                deteccion("Clorosis", 0.62, 0.12, 0.30, 0.18, 0.37),
                deteccion("Sano", 0.81, 0.50, 0.50, 0.60, 0.62)));

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capturaId").value(capturaId))
                .andExpect(jsonPath("$.estado").value("Clorosis"))
                .andExpect(jsonPath("$.conf").value(62.0))
                .andExpect(jsonPath("$.sev").value("Alta"))
                .andExpect(jsonPath("$.confidence").value("Media"))
                .andExpect(jsonPath("$.detectionCount").value(2))
                .andExpect(jsonPath("$.detections").doesNotExist())
                .andExpect(jsonPath("$.concluyente").value(true))
                // Versionada por el hash del contenido: un id repetido nunca reusa la foto en caché
                .andExpect(jsonPath("$.imagenUrl", matchesPattern(urlVersionada(capturaId))))
                // El sector y la zona se toman de la captura cuando no vienen explícitos.
                .andExpect(jsonPath("$.sectorId").value("MZ-2-005"))
                .andExpect(jsonPath("$.zonaId").value("MZ-2"));
    }

    @Test
    void alta_sinCaptura_devuelve400() throws Exception {
        // Todo diagnóstico nace del análisis de una imagen: sin captura no hay diagnóstico.
        Map<String, Object> body = anomalo(null, "Clorosis", 90);
        body.remove("capturaId");
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void alta_conCapturaInexistente_devuelve400() throws Exception {
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(anomalo("CAP-999999", "Clorosis", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conSectorInexistente_devuelve400() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 90);
        body.put("sectorId", "MZ-99-999");

        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conScoreFueraDeRango_devuelve400() throws Exception {
        String capturaId = capturaExistente();

        for (Object conf : new Object[]{-1, 101, 250.5}) {
            Map<String, Object> body = anomalo(capturaId, "Clorosis", 0);
            body.put("conf", conf);
            mvc.perform(post("/api/diagnosticos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void alta_conEstadoFueraDeLaTaxonomia_devuelve400() throws Exception {
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(anomalo(capturaExistente(), "Se ve feo", 90))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_anomaloSinSeveridad_devuelve400ConElCampo() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62);
        body.remove("sev");
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("sev")));
    }

    @Test
    void alta_sanoConSeveridad_devuelve400() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Sano", 90);
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_noConcluyenteConConfianza_devuelve400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("capturaId", capturaExistente());
        body.put("estado", "No concluyente");
        body.put("confidence", "Baja");
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alta_conCajaInvalida_rechazaElAltaCompleta() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62);
        body.put("detections", List.of(
                deteccion("Clorosis", 0.62, 0.12, 0.30, 0.18, 0.37),
                deteccion("Clorosis", 0.70, 0.40, 0.30, 0.20, 0.37)));
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void alta_conCajaQueNoEsNumerica_devuelve400() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62);
        body.put("detections", List.of(Map.of("label", "Clorosis", "score", 0.6,
                "box", List.of("a", "b", "c", "d"))));
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void alta_conMasDe2000Detecciones_devuelve400() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62);
        body.put("detections", java.util.Collections.nCopies(2001, deteccion("Clorosis", 0.6, 0.1, 0.1, 0.2, 0.2)));
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anomaloConConfianzaBaja_quedaConcluyente() throws Exception {
        // D9: concluyente = estado distinto de No concluyente; ya no depende de un umbral sobre el score.
        Map<String, Object> body = anomalo(capturaExistente(), "Daño biótico", 60.0);
        body.put("confidence", "Baja");
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.concluyente").value(true));
    }

    @Test
    void noConcluyente_seRegistraSinScoreNiConfianza() throws Exception {
        // Requiere migracion-diagnostico-detecciones.sql: ddl-auto no quita el NOT NULL de diagnostico.conf.
        String nullable = jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'diagnostico' AND column_name = 'conf'", String.class);
        Assumptions.assumeTrue("YES".equals(nullable),
                "diagnostico.conf sigue NOT NULL: falta correr migracion-diagnostico-detecciones.sql");

        Map<String, Object> body = new HashMap<>();
        body.put("capturaId", capturaExistente());
        body.put("estado", "No concluyente");
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conf").value((Object) null))
                .andExpect(jsonPath("$.confidence").value((Object) null))
                .andExpect(jsonPath("$.sev").value("—"))
                .andExpect(jsonPath("$.concluyente").value(false));
    }

    @Test
    void alta_daElMismoResultadoParaCualquierEmisor() throws Exception {
        // El servicio de inferencia y una carga manual son el mismo camino: no hay estado
        // global en el backend que condicione el alta, ni marca que distinga el origen. Si
        // alguien agrega una compuerta o un campo de procedencia, este test falla.
        String primera = alta(anomalo(capturaExistente(), "Estrés solar", 88.0));
        String segunda = alta(anomalo(capturaExistente(), "Estrés solar", 88.0));

        var a = json.readTree(primera);
        var b = json.readTree(segunda);

        // Mismos campos, mismos valores: sólo cambian el id y la captura.
        org.junit.jupiter.api.Assertions.assertEquals(a.get("estado"), b.get("estado"));
        org.junit.jupiter.api.Assertions.assertEquals(a.get("conf"), b.get("conf"));
        org.junit.jupiter.api.Assertions.assertEquals(a.get("sev"), b.get("sev"));
        org.junit.jupiter.api.Assertions.assertEquals(a.get("confidence"), b.get("confidence"));
        org.junit.jupiter.api.Assertions.assertEquals(a.get("concluyente"), b.get("concluyente"));
        // Y ninguno lleva marca de origen.
        org.junit.jupiter.api.Assertions.assertFalse(a.has("origen"));
        org.junit.jupiter.api.Assertions.assertFalse(b.has("origen"));
    }

    @Test
    void elDiagnosticoAparceEnElSnapshotDelVivero() throws Exception {
        String capturaId = capturaExistente();
        Map<String, Object> body = anomalo(capturaId, "Clorosis", 62.0);
        body.put("sev", "Baja");
        body.put("detections", List.of(deteccion("Clorosis", 0.62, 0.12, 0.30, 0.18, 0.37)));
        String creado = alta(body);
        String id = json.readTree(creado).get("id").asText();
        long creadoEn = json.readTree(creado).get("creadoEn").asLong();

        // Aparece como una tarjeta más, con su imagen real, indistinguible de una que emitiría
        // el modelo. El dashboard no sabe de dónde salió. Trae nivel y cantidad, no las cajas.
        mvc.perform(get("/api/nursery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnoses[?(@.id=='" + id + "')]").exists())
                .andExpect(jsonPath("$.diagnoses[?(@.id=='" + id + "')].imagenUrl",
                        hasItem(matchesPattern(urlVersionada(capturaId)))))
                // Con la fecha real del alta, no sólo el "hace N min" ya redactado
                .andExpect(jsonPath("$.diagnoses[?(@.id=='" + id + "')].creadoEn").value(creadoEn))
                .andExpect(jsonPath("$.diagById['" + id + "'].confidence").value("Media"))
                .andExpect(jsonPath("$.diagById['" + id + "'].detectionCount").value(1))
                .andExpect(jsonPath("$.diagById['" + id + "'].detections").doesNotExist())
                // El diagnóstico vigente del sector muestra Clorosis, Baja, Media y 62.
                .andExpect(jsonPath("$.byId['MZ-2-005'].diagnosis.estado").value("Clorosis"))
                .andExpect(jsonPath("$.byId['MZ-2-005'].diagnosis.sev").value("Baja"))
                .andExpect(jsonPath("$.byId['MZ-2-005'].diagnosis.confidence").value("Media"))
                .andExpect(jsonPath("$.byId['MZ-2-005'].diagnosis.conf").value(62.0));
    }

    @Test
    void lasDeteccionesSeConsultanAparte() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62.0);
        body.put("detections", List.of(
                deteccion("Clorosis", 0.62, 0.12, 0.30, 0.18, 0.37),
                deteccion("Sano", 0.8123, 0.5, 0.5, 0.6, 0.62)));
        String id = json.readTree(alta(body)).get("id").asText();

        mvc.perform(get("/api/diagnosticos/" + id + "/detecciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detections.length()").value(2))
                .andExpect(jsonPath("$.detections[0].label").value("Clorosis"))
                .andExpect(jsonPath("$.detections[0].score").value(0.62))
                .andExpect(jsonPath("$.detections[0].box.length()").value(4))
                .andExpect(jsonPath("$.detections[0].box[2]").value(0.18))
                .andExpect(jsonPath("$.detections[1].label").value("Sano"));
    }

    @Test
    void unaCargaManualSinDetecciones_devuelveListaVacia() throws Exception {
        String id = json.readTree(alta(anomalo(capturaExistente(), "Clorosis", 62.0))).get("id").asText();
        mvc.perform(get("/api/diagnosticos/" + id + "/detecciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detections").isArray())
                .andExpect(jsonPath("$.detections.length()").value(0))
                .andExpect(jsonPath("$.crop").value((Object) null));
    }

    @Test
    void elRecorteSeConsultaConLasDetecciones() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62.0);
        body.put("detections", List.of(deteccion("Clorosis", 0.62, 0.12, 0.30, 0.18, 0.37)));
        body.put("crop", List.of(0.2574, 0.2542, 1.0, 1.0));
        String id = json.readTree(alta(body)).get("id").asText();

        mvc.perform(get("/api/diagnosticos/" + id + "/detecciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detections.length()").value(1))
                .andExpect(jsonPath("$.crop.length()").value(4))
                .andExpect(jsonPath("$.crop[0]").value(0.2574))
                .andExpect(jsonPath("$.crop[3]").value(1.0));
    }

    @Test
    void recorteInvalido_400QueNombraElCampo() throws Exception {
        Map<String, Object> body = anomalo(capturaExistente(), "Clorosis", 62.0);
        body.put("crop", List.of(0.6, 0.2, 0.4, 1.0));
        mvc.perform(post("/api/diagnosticos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("crop")));
    }

    @Test
    void deteccionesDeUnDiagnosticoInexistente_devuelve404() throws Exception {
        for (String id : List.of("DX-99999", "DG-001")) {
            mvc.perform(get("/api/diagnosticos/" + id + "/detecciones"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").isString());
        }
    }

    @Test
    void losDiagnosticosDerivadosDeSectoresNoTraenImagen() throws Exception {
        // Las cards sintéticas (prefijo DG-) conviven con las persistidas (DX-) sin colisionar,
        // y recurren al gradiente porque no tienen fotografía ni detecciones.
        mvc.perform(get("/api/nursery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnoses[?(@.id =~ /DG-.*/)].imagenUrl").isEmpty())
                .andExpect(jsonPath("$.diagnoses[?(@.id =~ /DG-.*/ && @.detectionCount != 0)]").isEmpty())
                .andExpect(jsonPath("$.diagnoses[?(@.id =~ /DG-.*/ && @.estado == 'No concluyente' && @.conf != null)]").isEmpty());
    }

    @Test
    void listado_devuelveDelMasRecienteAlMasAntiguo() throws Exception {
        Map<String, Object> sano = new HashMap<>();
        sano.put("capturaId", capturaExistente());
        sano.put("estado", "Sano");
        sano.put("confidence", "Alta");
        sano.put("conf", 99.0);
        alta(sano);

        mvc.perform(get("/api/diagnosticos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].creadoEn").exists())
                .andExpect(jsonPath("$[0].detectionCount").isNumber())
                .andExpect(jsonPath("$[0].detections").doesNotExist());
    }

    /** Ruta de la imagen de una captura, con su versión de contenido ({@code ?v=} + 16 hex). */
    private static String urlVersionada(String capturaId) {
        return "/api/capturas/" + capturaId + "/imagen[?]v=[0-9a-f]{16}";
    }
}
