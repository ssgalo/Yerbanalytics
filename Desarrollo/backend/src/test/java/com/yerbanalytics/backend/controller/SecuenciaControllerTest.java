package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.IniciarSecuencia;
import com.yerbanalytics.backend.dto.LecturaSecuencia;
import com.yerbanalytics.backend.dto.ParametrosSecuencia;
import com.yerbanalytics.backend.dto.PasoSecuencia;
import com.yerbanalytics.backend.dto.Secuencia;
import com.yerbanalytics.backend.service.SecuenciaInvalidaException;
import com.yerbanalytics.backend.service.SecuenciaRechazadaException;
import com.yerbanalytics.backend.service.SecuenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de las secuencias (design add-secuencias-demo-expo §2.7): lo consume el dashboard, así que las claves no se tocan. */
@WebMvcTest(SecuenciaController.class)
@DisplayName("SecuenciaController - contrato")
class SecuenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecuenciaService service;

    private static Secuencia riego() {
        PasoSecuencia abrir = new PasoSecuencia(1, "ABRIR", "OK", null, null, "cmd-1", null,
                1760000000010L, 1760000000400L);
        PasoSecuencia esperar = new PasoSecuencia(2, "ESPERAR", "EN_CURSO", null, null, null,
                1760000015400L, 1760000000400L, null);
        PasoSecuencia cerrar = new PasoSecuencia(3, "CERRAR", "PENDIENTE", null, null, null, null, null, null);
        return new Secuencia("s-1", "RIEGO", "EN_CURSO", "MZ-1", "MZ-1-001", new ParametrosSecuencia(15, null),
                1760000000000L, null, false, null, null, List.of(abrir, esperar, cerrar));
    }

    private static Secuencia lectura() {
        Map<String, Double> metricas = new LinkedHashMap<>();
        for (String k : List.of("humSus", "humAmb", "temp", "tempSuelo", "uv", "ce", "phSuelo", "n", "p", "k")) {
            metricas.put(k, null);
        }
        metricas.put("humSus", 41.0);
        metricas.put("ce", 1.2);
        return new Secuencia("s-2", "LECTURA", "COMPLETADA", "MZ-1", null, new ParametrosSecuencia(null, null),
                1760000000000L, 1760000003200L, false, null,
                new LecturaSecuencia(1760000003200L, metricas), List.of());
    }

    @Test
    void postIniciaYResponde202ConLaSecuenciaCompleta() throws Exception {
        when(service.iniciar(any())).thenReturn(riego());

        mockMvc.perform(post("/api/secuencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RIEGO\",\"parametros\":{\"duracionSeg\":15}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("s-1"))
                .andExpect(jsonPath("$.tipo").value("RIEGO"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.zonaId").value("MZ-1"))
                .andExpect(jsonPath("$.sectorId").value("MZ-1-001"))
                .andExpect(jsonPath("$.parametros.duracionSeg").value(15))
                .andExpect(jsonPath("$.parametros.esperaSeg").value((Object) null))
                .andExpect(jsonPath("$.iniciadaEn").value(1760000000000L))
                .andExpect(jsonPath("$.finalizadaEn").value((Object) null))
                .andExpect(jsonPath("$.cancelacionSolicitada").value(false))
                .andExpect(jsonPath("$.error").value((Object) null))
                .andExpect(jsonPath("$.lectura").value((Object) null))
                .andExpect(jsonPath("$.pasos.length()").value(3))
                .andExpect(jsonPath("$.pasos[0].tipo").value("ABRIR"))
                .andExpect(jsonPath("$.pasos[1].esperaHasta").value(1760000015400L));

        ArgumentCaptor<IniciarSecuencia> c = ArgumentCaptor.forClass(IniciarSecuencia.class);
        verify(service).iniciar(c.capture());
        assertThat(c.getValue().tipo()).isEqualTo("RIEGO");
        assertThat(c.getValue().parametros().duracionSeg()).isEqualTo(15);
        assertThat(c.getValue().parametros().esperaSeg()).isNull();
    }

    @Test
    void elPostSinParametrosLlegaConParametrosNull() throws Exception {
        when(service.iniciar(any())).thenReturn(lectura());

        mockMvc.perform(post("/api/secuencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"LECTURA\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<IniciarSecuencia> c = ArgumentCaptor.forClass(IniciarSecuencia.class);
        verify(service).iniciar(c.capture());
        assertThat(c.getValue().tipo()).isEqualTo("LECTURA");
        assertThat(c.getValue().parametros()).isNull();
    }

    @Test
    void todasLasClavesDeLaSecuenciaYDelPasoEstanSiempreAunqueSeanNull() throws Exception {
        when(service.iniciar(any())).thenReturn(riego());

        String json = mockMvc.perform(post("/api/secuencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RIEGO\"}"))
                .andReturn().getResponse().getContentAsString();

        for (String clave : List.of("id", "tipo", "estado", "zonaId", "sectorId", "parametros", "iniciadaEn",
                "finalizadaEn", "cancelacionSolicitada", "error", "lectura", "pasos",
                "duracionSeg", "esperaSeg",
                "n", "codigoError", "detalle", "commandId", "esperaHasta", "iniciadoEn", "terminadoEn")) {
            assertThat(json).contains("\"" + clave + "\":");
        }
    }

    @Test
    void laLecturaSerializaRecibidaEnYLasMetricasConNull() throws Exception {
        when(service.estado()).thenReturn(Optional.of(lectura()));

        String json = mockMvc.perform(get("/api/secuencias/actual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorId").value((Object) null))
                .andExpect(jsonPath("$.lectura.recibidaEn").value(1760000003200L))
                .andExpect(jsonPath("$.lectura.metricas.humSus").value(41.0))
                .andExpect(jsonPath("$.lectura.metricas.ce").value(1.2))
                .andExpect(jsonPath("$.lectura.metricas.tempSuelo").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        for (String clave : List.of("humSus", "humAmb", "temp", "tempSuelo", "uv", "ce", "phSuelo", "n", "p", "k")) {
            assertThat(json).contains("\"" + clave + "\":");
        }
    }

    @Test
    void postInvalidoResponde400ConElMensaje() throws Exception {
        when(service.iniciar(any())).thenThrow(new SecuenciaInvalidaException("duracionSeg tiene que estar entre 1 y 1190."));

        mockMvc.perform(post("/api/secuencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RIEGO\",\"parametros\":{\"duracionSeg\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("duracionSeg tiene que estar entre 1 y 1190."));
    }

    @Test
    void postRechazadoResponde409ConElMensaje() throws Exception {
        when(service.iniciar(any())).thenThrow(new SecuenciaRechazadaException("Hay una pasada del riel en curso."));

        mockMvc.perform(post("/api/secuencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RIEGO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Hay una pasada del riel en curso."));
    }

    @Test
    void getActualResponde200ConLaSecuencia() throws Exception {
        when(service.estado()).thenReturn(Optional.of(riego()));

        mockMvc.perform(get("/api/secuencias/actual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s-1"));
    }

    @Test
    void getActualResponde204SiNuncaHuboUna() throws Exception {
        when(service.estado()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/secuencias/actual"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void cancelarResponde200ConLaSecuencia() throws Exception {
        when(service.cancelar()).thenReturn(riego());

        mockMvc.perform(post("/api/secuencias/actual/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s-1"));
    }

    @Test
    void cancelarSinSecuenciaResponde409() throws Exception {
        when(service.cancelar()).thenThrow(new SecuenciaRechazadaException("No hay una secuencia en curso."));

        mockMvc.perform(post("/api/secuencias/actual/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No hay una secuencia en curso."));
    }
}
