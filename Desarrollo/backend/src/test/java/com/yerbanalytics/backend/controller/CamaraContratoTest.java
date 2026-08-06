package com.yerbanalytics.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.config.CapturaProperties;
import com.yerbanalytics.backend.service.AlmacenamientoImagenService;
import com.yerbanalytics.backend.service.CapturaService;
import com.yerbanalytics.backend.service.TokenService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ejercita el contrato de dispositivo de captura de punta a punta contra el backend real.
 *
 * <p>Requiere PostgreSQL levantado, igual que {@link NurseryControllerTest}: es la convención
 * de tests de integración del repo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CamaraContratoTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CapturaProperties props;
    @Autowired private CapturaService capturaService;

    private static final byte[] JPEG = "bytes-de-un-jpeg-de-prueba".getBytes(StandardCharsets.UTF_8);

    /**
     * Los canales SSE viven en memoria y sobreviven entre tests: MockMvc no completa la
     * petición asíncrona, así que un emisor abierto en un test se lleva las órdenes del
     * siguiente. Se cierran antes de cada uno para que cada test controle qué dispositivos
     * están conectados.
     */
    @BeforeEach
    void aislarCanales() {
        capturaService.cerrarCanales();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Recorre vinculación → enrolamiento → token y devuelve el access token. */
    private String enrolarYObtenerToken(String nombre) throws Exception {
        String codigo = json.readTree(
                mvc.perform(post("/api/camara/vinculacion"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("codigo").asText();

        String refresh = json.readTree(
                mvc.perform(post("/api/camara/v1/enrolar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "codigo", codigo, "nombre", nombre, "plataforma", "test"))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("refreshToken").asText();

        return json.readTree(
                mvc.perform(post("/api/camara/v1/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("accessToken").asText();
    }

    private String emitirOrden() throws Exception {
        MvcResult r = mvc.perform(post("/api/capturas/ordenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sectorId", "MZ-1-001", "posicionRiel", 1420))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("ordenId").asText();
    }

    private String meta(byte[] bytes) throws Exception {
        return json.writeValueAsString(Map.of(
                "ancho", 1920, "alto", 1080,
                "sha256", AlmacenamientoImagenService.sha256(bytes),
                "capturadaEn", System.currentTimeMillis()));
    }

    private MockMultipartFile parteMeta(String metaJson) {
        return new MockMultipartFile("meta", "meta.json",
                MediaType.APPLICATION_JSON_VALUE, metaJson.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile parteImagen(byte[] bytes) {
        return new MockMultipartFile("imagen", "captura.jpg", MediaType.IMAGE_JPEG_VALUE, bytes);
    }

    // ------------------------------------------------------------------
    // Credenciales
    // ------------------------------------------------------------------

    @Test
    void enrolamiento_completoDevuelveTokenUsable() throws Exception {
        String token = enrolarYObtenerToken("iPhone test enrolamiento");

        mvc.perform(get("/api/camara/v1/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchoMax").value(props.getAnchoMax()))
                .andExpect(jsonPath("$.calidadJpeg").value(props.getCalidadJpeg()))
                .andExpect(jsonPath("$.maxColaOrdenes").value(props.getMaxColaOrdenes()));
    }

    @Test
    void enrolar_conCodigoYaConsumido_devuelve401() throws Exception {
        String codigo = json.readTree(
                mvc.perform(post("/api/camara/vinculacion")).andReturn().getResponse().getContentAsString()
        ).get("codigo").asText();

        String body = json.writeValueAsString(Map.of("codigo", codigo, "nombre", "primero"));
        mvc.perform(post("/api/camara/v1/enrolar").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // El código es de un solo uso: el segundo intento no debe enrolar nada.
        mvc.perform(post("/api/camara/v1/enrolar").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enrolar_conCodigoInexistente_devuelve401() throws Exception {
        mvc.perform(post("/api/camara/v1/enrolar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigo", "ZZZZ-ZZZZ", "nombre", "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_conCredencialInvalida_devuelve401() throws Exception {
        mvc.perform(post("/api/camara/v1/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", "no-existe"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dispositivoRevocado_noPuedeRenovarSuToken() throws Exception {
        String codigo = json.readTree(
                mvc.perform(post("/api/camara/vinculacion")).andReturn().getResponse().getContentAsString()
        ).get("codigo").asText();

        var enrolado = json.readTree(
                mvc.perform(post("/api/camara/v1/enrolar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(
                                        Map.of("codigo", codigo, "nombre", "a revocar"))))
                        .andReturn().getResponse().getContentAsString());

        String id = enrolado.get("dispositivoId").asText();
        String refresh = enrolado.get("refreshToken").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/camara/dispositivos/" + id))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/camara/v1/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Autenticación acotada por path
    // ------------------------------------------------------------------

    @Test
    void rutaDelContrato_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/camara/v1/config")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/camara/v1/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaDelContrato_conTokenVencido_devuelve401() throws Exception {
        CapturaProperties vencido = new CapturaProperties();
        vencido.setJwtSecret(props.getJwtSecret());
        vencido.setTokenVidaSeg(-1);
        String token = new TokenService(vencido).emitir("CAM-999");

        mvc.perform(get("/api/camara/v1/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headerAuthorization_funcionaEnElStream() throws Exception {
        String token = enrolarYObtenerToken("iPhone test stream header");

        // El contrato exige que el header sea la vía canónica TAMBIÉN en el stream: un cliente
        // Android no debería necesitar el query param.
        MvcResult r = mvc.perform(get("/api/camara/v1/ordenes/stream")
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
    }

    @Test
    void queryParam_funcionaEnElStream_paraClientesSinHeaders() throws Exception {
        String token = enrolarYObtenerToken("iPhone test stream query");

        MvcResult r = mvc.perform(get("/api/camara/v1/ordenes/stream").param("token", token))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
    }

    @Test
    void queryParam_noSirveFueraDelStream() throws Exception {
        String token = enrolarYObtenerToken("iPhone test query acotado");

        // La alternativa está acotada al stream a propósito: el token en la query string es
        // una concesión a EventSource, no la forma general de autenticar.
        mvc.perform(get("/api/camara/v1/config").param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elRestoDeLaApi_sigueRespondiendoSinToken() throws Exception {
        // El filtro está acotado por path a propósito: traer spring-security completo habría
        // protegido por defecto endpoints que hoy son abiertos.
        mvc.perform(get("/api/nursery")).andExpect(status().isOk());
        mvc.perform(get("/api/historial")).andExpect(status().isOk());
        mvc.perform(get("/api/configuracion")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Ciclo de la orden
    // ------------------------------------------------------------------

    @Test
    void ordenSinDispositivoConectado_quedaPendiente() throws Exception {
        String ordenId = emitirOrden();

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(1))
                .andExpect(jsonPath("$.capturaId").doesNotExist());
    }

    @Test
    void emitirOrden_conSectorInexistente_devuelve400() throws Exception {
        mvc.perform(post("/api/capturas/ordenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("sectorId", "MZ-99-999", "posicionRiel", 10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subirImagen_correlacionaConSuOrdenYQuedaRecibida() throws Exception {
        String token = enrolarYObtenerToken("iPhone test subida");
        String ordenId = emitirOrden();

        MvcResult r = mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG))
                        .file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordenId").value(ordenId))
                .andExpect(jsonPath("$.imagenUrl").exists())
                .andReturn();

        String capturaId = json.readTree(r.getResponse().getContentAsString()).get("capturaId").asText();
        assertNotNull(capturaId);

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.capturaId").value(capturaId));

        // La imagen se sirve inmutable y con el sha256 como ETag.
        mvc.perform(get("/api/capturas/" + capturaId + "/imagen"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + AlmacenamientoImagenService.sha256(JPEG) + "\""))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("immutable")));
    }

    @Test
    void subirImagen_sobreOrdenYaResuelta_devuelve409ConLaCapturaExistente() throws Exception {
        String token = enrolarYObtenerToken("iPhone test idempotencia");
        String ordenId = emitirOrden();

        MvcResult primera = mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        String capturaId = json.readTree(primera.getResponse().getContentAsString())
                .get("capturaId").asText();

        // Reintento tras un timeout de red cuya primera subida sí llegó. El contrato obliga al
        // cliente a tratarlo como éxito, así que la respuesta lleva el capturaId existente.
        mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.capturaId").value(capturaId));
    }

    @Test
    void subirImagen_desdeUnDispositivoAjeno_devuelve403() throws Exception {
        String tokenA = enrolarYObtenerToken("iPhone dueño de la orden");
        String tokenB = enrolarYObtenerToken("iPhone entrometido");

        // A abre su canal, así que la orden se le entrega a él.
        mvc.perform(get("/api/camara/v1/ordenes/stream").header("Authorization", "Bearer " + tokenA))
                .andExpect(request().asyncStarted());
        String ordenId = emitirOrden();

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));

        // B intenta responderla: la imagen se descarta, porque no se le entregó a él.
        mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // A sí puede.
        mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());
    }

    @Test
    void abrirCanal_drenaLasOrdenesPendientes() throws Exception {
        String token = enrolarYObtenerToken("iPhone drenado");

        // Orden emitida con el dispositivo desconectado: no se pierde, queda esperando.
        String ordenId = emitirOrden();
        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        mvc.perform(get("/api/camara/v1/ordenes/stream").header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted());

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));
    }

    @Test
    void subirImagen_conOrdenInexistente_devuelve404() throws Exception {
        String token = enrolarYObtenerToken("iPhone test 404");

        mvc.perform(multipart("/api/camara/v1/ordenes/no-existe/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(meta(JPEG)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void subirImagen_conHashQueNoCoincide_devuelve422() throws Exception {
        String token = enrolarYObtenerToken("iPhone test hash");
        String ordenId = emitirOrden();

        String metaMentirosa = json.writeValueAsString(Map.of(
                "ancho", 1920, "alto", 1080,
                "sha256", AlmacenamientoImagenService.sha256("otra-cosa".getBytes(StandardCharsets.UTF_8)),
                "capturadaEn", System.currentTimeMillis()));

        mvc.perform(multipart("/api/camara/v1/ordenes/" + ordenId + "/imagen")
                        .file(parteImagen(JPEG)).file(parteMeta(metaMentirosa))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // La orden vuelve al circuito de reintento, no queda perdida.
        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(2));
    }

    @Test
    void acusarFallo_conMotivoDesconocido_devuelve400() throws Exception {
        String token = enrolarYObtenerToken("iPhone test motivo");
        String ordenId = emitirOrden();

        // El conjunto es cerrado a propósito: si admitiera texto libre, el backend no podría
        // clasificar los fallos sin interpretarlo.
        mvc.perform(post("/api/camara/v1/ordenes/" + ordenId + "/fallo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "SE_ME_CAYO_EL_TELEFONO"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acusarFallo_conMotivoValido_reencolaLaOrden() throws Exception {
        String token = enrolarYObtenerToken("iPhone test fallo");
        String ordenId = emitirOrden();

        mvc.perform(post("/api/camara/v1/ordenes/" + ordenId + "/fallo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("motivo", "COLA_LLENA", "detalle", "tope alcanzado"))))
                .andExpect(status().isAccepted());

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(2))
                .andExpect(jsonPath("$.motivoFallo").value("COLA_LLENA"));
    }

    @Test
    void ordenAgotaSusIntentos_yTerminaEnError() throws Exception {
        String token = enrolarYObtenerToken("iPhone test intentos");
        String ordenId = emitirOrden();

        for (int i = 0; i < props.getMaxIntentos(); i++) {
            mvc.perform(post("/api/camara/v1/ordenes/" + ordenId + "/fallo")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("motivo", "CAMARA_NO_LISTA"))));
        }

        mvc.perform(get("/api/capturas/ordenes/" + ordenId))
                .andExpect(jsonPath("$.estado").value("ERROR"));

        // Un estado terminal no admite más acuses.
        mvc.perform(post("/api/camara/v1/ordenes/" + ordenId + "/fallo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "CAMARA_NO_LISTA"))))
                .andExpect(status().isConflict());
    }

    @Test
    void imagenDeCapturaInexistente_devuelve404() throws Exception {
        mvc.perform(get("/api/capturas/CAP-999999/imagen")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Helpers estáticos de MockMvc que no vienen del import por defecto
    // ------------------------------------------------------------------

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }

    private static org.springframework.test.web.servlet.result.RequestResultMatchers request() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.request();
    }
}
