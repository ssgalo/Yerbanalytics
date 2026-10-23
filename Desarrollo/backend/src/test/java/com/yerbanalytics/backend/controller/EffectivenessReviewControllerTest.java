package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.EffectivenessReview;
import com.yerbanalytics.backend.effectiveness.EffectivenessReviewService;
import com.yerbanalytics.backend.exception.EffectivenessEventNotFoundException;
import com.yerbanalytics.backend.exception.EffectivenessNotBlockingException;
import com.yerbanalytics.backend.exception.InvalidEffectivenessReviewException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de la revisión de una acción sin efectividad (S-06). */
@WebMvcTest(EffectivenessReviewController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EffectivenessReviewController - contrato")
class EffectivenessReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EffectivenessReviewService service;

    @Test
    @DisplayName("POST registra la revisión con el usuario de la sesión y responde cuántos bloqueos levantó")
    void postRegistersTheReview() throws Exception {
        when(service.register(eq("ev-1"), eq("Destapé el emisor"), eq("Ana")))
                .thenReturn(new EffectivenessReview("ev-1", "IRRIGATION", 2, 1_000L));

        mockMvc.perform(post("/api/efectividad/ev-1/revision").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":\"Destapé el emisor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("ev-1"))
                .andExpect(jsonPath("$.kind").value("IRRIGATION"))
                .andExpect(jsonPath("$.clearedEvents").value(2))
                .andExpect(jsonPath("$.reviewedTs").value(1_000));
        verify(service).register("ev-1", "Destapé el emisor", "Ana");
    }

    @Test
    @DisplayName("sin detalle el servicio rechaza y responde 400 con el motivo")
    void missingDetailIs400() throws Exception {
        when(service.register(eq("ev-1"), eq(null), any()))
                .thenThrow(new InvalidEffectivenessReviewException("El detalle de la revisión es obligatorio."));

        mockMvc.perform(post("/api/efectividad/ev-1/revision"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El detalle de la revisión es obligatorio."));
    }

    @Test
    @DisplayName("un cuerpo que no es JSON responde 400")
    void malformedBodyIs400() throws Exception {
        mockMvc.perform(post("/api/efectividad/ev-1/revision")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("un evento inexistente responde 404")
    void unknownEventIs404() throws Exception {
        when(service.register(eq("zz"), any(), any())).thenThrow(new EffectivenessEventNotFoundException("zz"));

        mockMvc.perform(post("/api/efectividad/zz/revision")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":\"ok\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El evento zz no existe."));
    }

    @Test
    @DisplayName("un evento que ya no bloquea responde 409")
    void notBlockingIs409() throws Exception {
        when(service.register(eq("ev-1"), any(), any())).thenThrow(new EffectivenessNotBlockingException("ev-1"));

        mockMvc.perform(post("/api/efectividad/ev-1/revision")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":\"ok\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
