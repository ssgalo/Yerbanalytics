package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NutritionPlan;
import com.yerbanalytics.backend.exception.InvalidNutritionIntervalException;
import com.yerbanalytics.backend.exception.NutritionZoneNotFoundException;
import com.yerbanalytics.backend.nutrition.NutritionPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST del intervalo del plan de nutrición: lo consume el detalle de la macro-zona. */
@WebMvcTest(NutritionPlanController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NutritionPlanController - contrato")
class NutritionPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NutritionPlanService service;

    @Test
    @DisplayName("PUT fija el intervalo, responde con el plan y toma al usuario de la sesión")
    void putSetsTheInterval() throws Exception {
        when(service.setInterval(eq("MZ-3"), eq(14), eq("Ana")))
                .thenReturn(new NutritionPlan(14, true, 1_000L, 2_000L, false, false));

        mockMvc.perform(put("/api/zonas/MZ-3/plan-nutricion").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":14}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(14))
                .andExpect(jsonPath("$.customInterval").value(true))
                .andExpect(jsonPath("$.lastApplicationTs").value(1_000))
                .andExpect(jsonPath("$.nextDueTs").value(2_000))
                .andExpect(jsonPath("$.batchOpen").value(false))
                .andExpect(jsonPath("$.advanced").value(false));
        verify(service).setInterval("MZ-3", 14, "Ana");
    }

    @Test
    @DisplayName("un intervalo fuera de rango responde 400 con el motivo")
    void outOfRangeIs400() throws Exception {
        when(service.setInterval(eq("MZ-3"), eq(2), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvalidNutritionIntervalException("El intervalo del plan de nutrición debe estar entre 3 y 30 días."));

        mockMvc.perform(put("/api/zonas/MZ-3/plan-nutricion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El intervalo del plan de nutrición debe estar entre 3 y 30 días."));
    }

    @Test
    @DisplayName("un cuerpo que no es JSON responde 400")
    void malformedBodyIs400() throws Exception {
        mockMvc.perform(put("/api/zonas/MZ-3/plan-nutricion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":\"siete\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("sin cuerpo el servicio rechaza el intervalo faltante")
    void missingBodyReachesTheServiceAsNoInterval() throws Exception {
        when(service.setInterval(eq("MZ-3"), eq(null), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvalidNutritionIntervalException("Falta el intervalo"));

        mockMvc.perform(put("/api/zonas/MZ-3/plan-nutricion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el intervalo"));
    }

    @Test
    @DisplayName("una macro-zona inexistente responde 404")
    void unknownZoneIs404() throws Exception {
        when(service.setInterval(eq("MZ-9"), eq(7), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new NutritionZoneNotFoundException("MZ-9"));

        mockMvc.perform(put("/api/zonas/MZ-9/plan-nutricion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalDays\":7}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La macro-zona MZ-9 no existe."));
    }
}
