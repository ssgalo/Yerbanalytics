package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 5.1: contrato HTTP de {@code /api/configuracion} tras mudar dos campos al catálogo. */
@WebMvcTest(ConfiguracionController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ConfiguracionController - contrato")
class ConfiguracionControllerContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfiguracionService service;

    private static Configuracion cfg() {
        return new Configuracion(List.of(), new ConfiguracionOperativa(240, "Ana", 1L, false, "11:00", 14580));
    }

    @Test
    void get_laOperativaNoIncluyeLosCamposMudadosNiElVolumenDiario() throws Exception {
        when(service.getConfiguracion()).thenReturn(cfg());

        mockMvc.perform(get("/api/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operativa.riegoVolMaxDiarioMl").doesNotExist())
                // Se mudaron al catálogo del motor (seguimiento.*) o se borraron (insumoDosisMax24hMl): ver add-motor-reglas-operativo.
                .andExpect(jsonPath("$.operativa.insumoDosisMax24hMl").doesNotExist())
                .andExpect(jsonPath("$.operativa.seguimientoLatenciaMin").doesNotExist())
                .andExpect(jsonPath("$.operativa.seguimientoDeltaMin").doesNotExist())
                .andExpect(jsonPath("$.operativa.riegoTiempoMaxSeg").doesNotExist())
                .andExpect(jsonPath("$.operativa.mediasombraAperturaMaxPct").doesNotExist());
    }

    @Test
    void put_sinLosCamposMudadosResponde200() throws Exception {
        when(service.updateConfiguracion(any(), eq("Ana"))).thenReturn(cfg());

        mockMvc.perform(put("/api/configuracion").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{"
                                + "\"insumoDosisMax24hMl\":15,\"seguimientoLatenciaMin\":2,\"seguimientoDeltaMin\":5.0,"
                                + "\"intervaloSensadoMinutos\":240}}"))
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

    @Test
    void get_exponeLaPasadaDiariaYElUmbralEfectivoDeSoloLectura() throws Exception {
        when(service.getConfiguracion()).thenReturn(cfg());

        mockMvc.perform(get("/api/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operativa.pasadaDiariaActiva").value(false))
                .andExpect(jsonPath("$.operativa.pasadaDiariaHora").value("11:00"))
                .andExpect(jsonPath("$.operativa.effectiveStaleThresholdSeconds").value(14580))
                .andExpect(jsonPath("$.operativa.intervaloSensadoMinutos").value(240))
                .andExpect(jsonPath("$.operativa.sensingPullIntervalSeconds").doesNotExist())
                .andExpect(jsonPath("$.operativa.intervaloEvaluacionMinutos").doesNotExist());
    }

    @Test
    void put_aceptaLaPasadaDiariaEIgnoraLosCamposRetirados() throws Exception {
        when(service.updateConfiguracion(any(), any())).thenReturn(cfg());

        mockMvc.perform(put("/api/configuracion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{"
                                + "\"insumoDosisMax24hMl\":15,\"seguimientoLatenciaMin\":2,\"seguimientoDeltaMin\":5.0,"
                                + "\"intervaloSensadoMinutos\":240,\"intervaloEvaluacionMinutos\":5,"
                                + "\"sensingPullIntervalSeconds\":30,"
                                + "\"pasadaDiariaActiva\":true,\"pasadaDiariaHora\":\"10:30\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Configuracion> sent = ArgumentCaptor.forClass(Configuracion.class);
        verify(service).updateConfiguracion(sent.capture(), any());
        assertThat(sent.getValue().operativa().pasadaDiariaActiva()).isTrue();
        assertThat(sent.getValue().operativa().pasadaDiariaHora()).isEqualTo("10:30");
    }

    @Test
    void put_sinLaPasadaDiariaLaDeserializaComoNula() throws Exception {
        when(service.updateConfiguracion(any(), any())).thenReturn(cfg());

        mockMvc.perform(put("/api/configuracion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{"
                                + "\"insumoDosisMax24hMl\":15,\"seguimientoLatenciaMin\":2,\"seguimientoDeltaMin\":5.0,"
                                + "\"intervaloSensadoMinutos\":240}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Configuracion> sent = ArgumentCaptor.forClass(Configuracion.class);
        verify(service).updateConfiguracion(sent.capture(), any());
        assertThat(sent.getValue().operativa().pasadaDiariaActiva()).isNull();
        assertThat(sent.getValue().operativa().pasadaDiariaHora()).isNull();
    }

    @Test
    void put_horaInvalida_responde400NombrandoElParametro() throws Exception {
        when(service.updateConfiguracion(any(), any())).thenThrow(new InvalidConfigurationException(
                "pasadaDiariaHora: la hora de la pasada diaria debe tener el formato HH:mm, entre 00:00 y 23:59."));

        mockMvc.perform(put("/api/configuracion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"umbrales\":[],\"rustificacion\":[],\"operativa\":{"
                                + "\"insumoDosisMax24hMl\":15,\"seguimientoLatenciaMin\":2,\"seguimientoDeltaMin\":5.0,"
                                + "\"intervaloSensadoMinutos\":240,\"pasadaDiariaHora\":\"25:00\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pasadaDiariaHora")));
    }
}
