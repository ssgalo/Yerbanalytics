package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NewTankLoad;
import com.yerbanalytics.backend.dto.TankLevel;
import com.yerbanalytics.backend.exception.InvalidTankLoadException;
import com.yerbanalytics.backend.exception.TankZoneNotFoundException;
import com.yerbanalytics.backend.supply.Supply;
import com.yerbanalytics.backend.supply.TankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de los tanques de insumo: lo consume el dashboard, así que las claves no se tocan. */
@WebMvcTest(SupplyTankController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SupplyTankController - contrato")
class SupplyTankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TankService service;

    private static TankLevel level() {
        return new TankLevel("MZ-1", "FERTILIZER", "Fertilizante", 50_000, 20_000, 40.0, false, 1_759_514_400_000L, "Ana");
    }

    @Test
    @DisplayName("GET lista los tanques con nivel, porcentaje y marca de reposición")
    void getListsTheTanks() throws Exception {
        when(service.list()).thenReturn(List.of(level()));

        mockMvc.perform(get("/api/tanques"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneId").value("MZ-1"))
                .andExpect(jsonPath("$[0].supply").value("FERTILIZER"))
                .andExpect(jsonPath("$[0].supplyLabel").value("Fertilizante"))
                .andExpect(jsonPath("$[0].capacityMl").value(50_000))
                .andExpect(jsonPath("$[0].levelMl").value(20_000))
                .andExpect(jsonPath("$[0].levelPct").value(40.0))
                .andExpect(jsonPath("$[0].low").value(false))
                .andExpect(jsonPath("$[0].updatedBy").value("Ana"));
    }

    @Test
    @DisplayName("POST de una recarga responde 200 con el tanque y toma al usuario de la sesión")
    void postLoadsTheTank() throws Exception {
        when(service.load(eq("MZ-1"), eq(Supply.FERTILIZER), any(NewTankLoad.class), eq("Ana"))).thenReturn(level());

        mockMvc.perform(post("/api/tanques/MZ-1/FERTILIZER/recarga").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liters\":20,\"capacityLiters\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelMl").value(20_000));
        verify(service).load("MZ-1", Supply.FERTILIZER, new NewTankLoad(20.0, 50.0), "Ana");
    }

    @Test
    @DisplayName("POST con una carga inválida es 400 con el motivo")
    void postWithAnInvalidLoadIs400() throws Exception {
        when(service.load(any(), any(), any(), any())).thenThrow(new InvalidTankLoadException("La carga no entra."));

        mockMvc.perform(post("/api/tanques/MZ-1/FERTILIZER/recarga").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liters\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La carga no entra."));
    }

    @Test
    @DisplayName("POST a una zona inexistente es 404")
    void postToAnUnknownZoneIs404() throws Exception {
        when(service.load(any(), any(), any(), any())).thenThrow(new TankZoneNotFoundException("MZ-9"));

        mockMvc.perform(post("/api/tanques/MZ-9/FERTILIZER/recarga").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liters\":1,\"capacityLiters\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La macro-zona MZ-9 no existe."));
    }

    @Test
    @DisplayName("POST con un insumo que no existe es 400")
    void postWithAnUnknownSupplyIs400() throws Exception {
        mockMvc.perform(post("/api/tanques/MZ-1/AGUA/recarga").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liters\":1,\"capacityLiters\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST con un cuerpo ilegible es 400")
    void postWithABrokenBodyIs400() throws Exception {
        mockMvc.perform(post("/api/tanques/MZ-1/FERTILIZER/recarga").contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest());
    }
}
