package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.ManualLock;
import com.yerbanalytics.backend.dto.NewManualLock;
import com.yerbanalytics.backend.exception.InvalidManualLockException;
import com.yerbanalytics.backend.exception.ManualLockConflictException;
import com.yerbanalytics.backend.exception.ManualLockNotFoundException;
import com.yerbanalytics.backend.service.ManualLockService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST del bloqueo manual: lo consume el dashboard, así que las claves no se tocan. */
@WebMvcTest(ManualLockController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ManualLockController - contrato")
class ManualLockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManualLockService service;

    private static ManualLock lock() {
        return new ManualLock(42L, "MZ-2", "MZ-2-001", "Ana", 1_759_514_400_000L, "mantenimiento");
    }

    @Test
    @DisplayName("POST responde 201 con el bloqueo y sus claves en español")
    void postCreatesAndReturnsTheLock() throws Exception {
        when(service.activate(any(NewManualLock.class), eq("Ana"))).thenReturn(lock());

        mockMvc.perform(post("/api/bloqueos").header("X-Usuario", "Ana").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zonaId\":\"MZ-2\",\"sectorId\":\"MZ-2-001\",\"motivo\":\"mantenimiento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.zonaId").value("MZ-2"))
                .andExpect(jsonPath("$.sectorId").value("MZ-2-001"))
                .andExpect(jsonPath("$.activadoPor").value("Ana"))
                .andExpect(jsonPath("$.activadoTs").value(1_759_514_400_000L))
                .andExpect(jsonPath("$.motivo").value("mantenimiento"));
        verify(service).activate(new NewManualLock("MZ-2", "MZ-2-001", "mantenimiento"), "Ana");
    }

    @Test
    @DisplayName("POST sin zona es 400 con el motivo del rechazo")
    void postWithoutZoneIs400() throws Exception {
        when(service.activate(any(), any())).thenThrow(new InvalidManualLockException("La macro-zona es obligatoria."));

        mockMvc.perform(post("/api/bloqueos").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La macro-zona es obligatoria."));
    }

    @Test
    @DisplayName("POST con un cuerpo ilegible es 400")
    void postWithABrokenBodyIs400() throws Exception {
        mockMvc.perform(post("/api/bloqueos").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST de un alcance ya bloqueado es 409")
    void postOfALockedScopeIs409() throws Exception {
        when(service.activate(any(), any())).thenThrow(new ManualLockConflictException("ya bloqueado"));

        mockMvc.perform(post("/api/bloqueos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zonaId\":\"MZ-2\",\"motivo\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ya bloqueado"));
    }

    @Test
    @DisplayName("GET lista los bloqueos y pasa los filtros zona y sector")
    void getListsWithFilters() throws Exception {
        when(service.list("MZ-2", "MZ-2-001")).thenReturn(List.of(lock()));

        mockMvc.perform(get("/api/bloqueos").param("zona", "MZ-2").param("sector", "MZ-2-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(42));
    }

    @Test
    @DisplayName("GET sin filtros pide todo")
    void getWithoutFiltersAsksForEverything() throws Exception {
        when(service.list(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/bloqueos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("DELETE reanuda y responde 204")
    void deleteReleasesAndIs204() throws Exception {
        mockMvc.perform(delete("/api/bloqueos/42").header("X-Usuario", "Beto")).andExpect(status().isNoContent());

        verify(service).release(42L, "Beto");
    }

    @Test
    @DisplayName("DELETE de un bloqueo inexistente es 404")
    void deleteOfAnUnknownLockIs404() throws Exception {
        org.mockito.Mockito.doThrow(new ManualLockNotFoundException("no existe")).when(service).release(eq(9L), any());

        mockMvc.perform(delete("/api/bloqueos/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("no existe"));
    }
}
