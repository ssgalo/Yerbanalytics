package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.FungicideReview;
import com.yerbanalytics.backend.exception.InvalidFungicideReviewException;
import com.yerbanalytics.backend.exception.ReviewSectorNotFoundException;
import com.yerbanalytics.backend.supply.FungicideReviewService;
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

/** Contrato REST de la revisión de un sector que alcanzó el tope de fitosanitarios (F-06). */
@WebMvcTest(FungicideReviewController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("FungicideReviewController - contrato")
class FungicideReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FungicideReviewService service;

    @Test
    @DisplayName("POST registra la revisión con el usuario de la sesión y responde con la hora")
    void postRegistersTheReview() throws Exception {
        when(service.register(eq("MZ-1-045"), eq("Revisé las hojas"), eq("Ana")))
                .thenReturn(new FungicideReview("MZ-1-045", 1_000L));

        mockMvc.perform(post("/api/sectores/MZ-1-045/revision-fitosanitario").header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":\"Revisé las hojas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorId").value("MZ-1-045"))
                .andExpect(jsonPath("$.reviewedTs").value(1_000));
        verify(service).register("MZ-1-045", "Revisé las hojas", "Ana");
    }

    @Test
    @DisplayName("sin detalle el servicio rechaza y responde 400 con el motivo")
    void missingDetailIs400() throws Exception {
        when(service.register(eq("MZ-1-045"), eq(null), any()))
                .thenThrow(new InvalidFungicideReviewException("El detalle de la revisión es obligatorio."));

        mockMvc.perform(post("/api/sectores/MZ-1-045/revision-fitosanitario"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El detalle de la revisión es obligatorio."));
    }

    @Test
    @DisplayName("un cuerpo que no es JSON responde 400")
    void malformedBodyIs400() throws Exception {
        mockMvc.perform(post("/api/sectores/MZ-1-045/revision-fitosanitario")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("un sector inexistente responde 404")
    void unknownSectorIs404() throws Exception {
        when(service.register(eq("MZ-9-001"), any(), any()))
                .thenThrow(new ReviewSectorNotFoundException("MZ-9-001"));

        mockMvc.perform(post("/api/sectores/MZ-9-001/revision-fitosanitario")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"detail\":\"ok\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El sector MZ-9-001 no existe."));
    }
}
