package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.HardwareData;
import com.yerbanalytics.backend.exception.HardwareConflictException;
import com.yerbanalytics.backend.exception.InvalidHardwareException;
import com.yerbanalytics.backend.service.HardwareFaultService;
import com.yerbanalytics.backend.service.HardwareService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de la reparación (S-05): lo consume el dashboard, así que las claves no se tocan. */
@WebMvcTest(HardwareController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("HardwareController - registro de reparación")
class HardwareControllerReparacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HardwareService hardwareService;

    @MockBean
    private HardwareFaultService faultService;

    @Test
    @DisplayName("POST /{id}/reparacion registra con el detalle y el usuario, y responde el panel de hardware actualizado")
    void registraYResponde() throws Exception {
        when(hardwareService.getHardware()).thenReturn(new HardwareData(List.of(), 0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(post("/api/hardware/DEV-101/reparacion").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detalle\":\"Se destapó el emisor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averiados").value(0))
                .andExpect(jsonPath("$.dispositivos").isArray());

        verify(faultService).repair("DEV-101", "Se destapó el emisor", "Ana");
    }

    @Test
    @DisplayName("sin cuerpo el servicio recibe un detalle nulo y es él quien lo rechaza (400)")
    void sinCuerpo() throws Exception {
        doThrow(new InvalidHardwareException("El detalle de la reparación es obligatorio."))
                .when(faultService).repair(eq("DEV-101"), eq(null), any());

        mockMvc.perform(post("/api/hardware/DEV-101/reparacion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El detalle de la reparación es obligatorio."));
    }

    @Test
    @DisplayName("un dispositivo sin falla registrada es 409")
    void sinFalla() throws Exception {
        doThrow(new HardwareConflictException("El dispositivo «DEV-101» no tiene una falla registrada."))
                .when(faultService).repair(eq("DEV-101"), any(), any());

        mockMvc.perform(post("/api/hardware/DEV-101/reparacion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detalle\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El dispositivo «DEV-101» no tiene una falla registrada."));
    }
}
