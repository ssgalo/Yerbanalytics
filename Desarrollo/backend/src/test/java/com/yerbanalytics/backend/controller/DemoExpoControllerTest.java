package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.service.PreferenciaDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoExpoController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DemoExpoController")
class DemoExpoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PreferenciaDashboardService service;

    @Test
    void getDevuelveElValorActual() throws Exception {
        when(service.demoExpoVisible()).thenReturn(false);

        mockMvc.perform(get("/api/configuracion/demo-expo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void putPersisteYDevuelveElNuevoValor() throws Exception {
        when(service.cambiarDemoExpo(true)).thenReturn(true);

        mockMvc.perform(put("/api/configuracion/demo-expo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true));
    }

    @Test
    void putSinVisibleEs400() throws Exception {
        mockMvc.perform(put("/api/configuracion/demo-expo")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/configuracion/demo-expo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":null}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).cambiarDemoExpo(true);
        verify(service, never()).cambiarDemoExpo(false);
    }
}
