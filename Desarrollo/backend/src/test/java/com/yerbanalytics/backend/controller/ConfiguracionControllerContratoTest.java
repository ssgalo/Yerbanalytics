package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 5.1: contrato HTTP de {@code /api/configuracion} tras mudar dos campos al catálogo. */
@WebMvcTest(ConfiguracionController.class)
@DisplayName("ConfiguracionController - contrato")
class ConfiguracionControllerContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfiguracionService service;

    private static Configuracion cfg() {
        return new Configuracion(List.of(), new ConfiguracionOperativa(2000, 15, 2, 5.0, 240, 5, "Ana", 1L), List.of());
    }

    @Test
    void get_laOperativaNoIncluyeLosCamposMudados() throws Exception {
        when(service.getConfiguracion()).thenReturn(cfg());

        mockMvc.perform(get("/api/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operativa.riegoVolMaxDiarioMl").value(2000.0))
                .andExpect(jsonPath("$.operativa.riegoTiempoMaxSeg").doesNotExist())
                .andExpect(jsonPath("$.operativa.mediasombraAperturaMaxPct").doesNotExist());
    }

    @Test
    void put_sinLosCamposMudadosResponde200() throws Exception {
        when(service.updateConfiguracion(any(), eq("Ana"))).thenReturn(cfg());

        mockMvc.perform(put("/api/configuracion").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{\"riegoVolMaxDiarioMl\":2000,"
                                + "\"insumoDosisMax24hMl\":15,\"seguimientoLatenciaMin\":2,\"seguimientoDeltaMin\":5.0,"
                                + "\"intervaloSensadoMinutos\":240,\"intervaloEvaluacionMinutos\":5}}"))
                .andExpect(status().isOk());

        verify(service).updateConfiguracion(any(), eq("Ana"));
    }

    @Test
    void put_unClienteViejoQueTodaviaManda_losCamposMudadosNoRompe() throws Exception {
        when(service.updateConfiguracion(any(), any())).thenReturn(cfg());

        mockMvc.perform(put("/api/configuracion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{\"riegoTiempoMaxSeg\":120,"
                                + "\"riegoVolMaxDiarioMl\":2000,\"insumoDosisMax24hMl\":15,"
                                + "\"mediasombraAperturaMaxPct\":100,\"seguimientoLatenciaMin\":2,"
                                + "\"seguimientoDeltaMin\":5.0,\"intervaloSensadoMinutos\":240,"
                                + "\"intervaloEvaluacionMinutos\":5}}"))
                .andExpect(status().isOk());
    }
}
