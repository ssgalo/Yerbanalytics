package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DiagnosticoPaso;
import com.yerbanalytics.backend.dto.Pasada;
import com.yerbanalytics.backend.dto.PasoPasada;
import com.yerbanalytics.backend.service.PasadaRechazadaException;
import com.yerbanalytics.backend.service.PasadaRielService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de la pasada (design §2.6): lo consume el dashboard, así que las claves no se tocan. */
@WebMvcTest(PasadaRielController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PasadaRielController - contrato")
class PasadaRielControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasadaRielService service;

    private static Pasada pasada() {
        PasoPasada mover = new PasoPasada(1, "MOVER", 1, null, "OK", null, null, "cmd-1",
                null, null, null, null, null, 1759514400010L, 1759514421800L);
        PasoPasada foto = new PasoPasada(2, "CAPTURAR", 1, "MZ-1-001", "OK", null, null, null,
                "orden-1", "RECIBIDA", "CAP-000042", "/api/capturas/CAP-000042/imagen",
                new DiagnosticoPaso("Clorosis", 87.0, "Media", 1759514530000L), 1759514422000L, 1759514431000L);
        return new Pasada("p-1", "EN_CURSO", 1759514400000L, null, false, null, List.of(mover, foto));
    }

    @Test
    void postIniciaYResponde202ConLaPasadaCompleta() throws Exception {
        when(service.iniciar()).thenReturn(pasada());

        mockMvc.perform(post("/api/pasadas"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("p-1"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.iniciadaEn").value(1759514400000L))
                .andExpect(jsonPath("$.finalizadaEn").value((Object) null))
                .andExpect(jsonPath("$.cancelacionSolicitada").value(false))
                .andExpect(jsonPath("$.error").value((Object) null))
                .andExpect(jsonPath("$.pasos.length()").value(2));
    }

    @Test
    void todasLasClavesDelPasoEstanSiempreAunqueSeanNull() throws Exception {
        when(service.iniciar()).thenReturn(pasada());

        String json = mockMvc.perform(post("/api/pasadas")).andReturn().getResponse().getContentAsString();

        for (String clave : List.of("n", "tipo", "posicion", "sectorId", "estado", "codigoError", "detalle",
                "commandId", "ordenId", "estadoOrden", "capturaId", "imagenUrl", "diagnostico",
                "iniciadoEn", "terminadoEn")) {
            org.assertj.core.api.Assertions.assertThat(json).contains("\"" + clave + "\":");
        }
    }

    @Test
    void elDiagnosticoSeSerializaConSusCuatroClaves() throws Exception {
        when(service.estado()).thenReturn(Optional.of(pasada()));

        mockMvc.perform(get("/api/pasadas/actual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pasos[1].imagenUrl").value("/api/capturas/CAP-000042/imagen"))
                .andExpect(jsonPath("$.pasos[1].diagnostico.estado").value("Clorosis"))
                .andExpect(jsonPath("$.pasos[1].diagnostico.conf").value(87.0))
                .andExpect(jsonPath("$.pasos[1].diagnostico.sev").value("Media"))
                .andExpect(jsonPath("$.pasos[1].diagnostico.creadoEn").value(1759514530000L))
                .andExpect(jsonPath("$.pasos[0].diagnostico").value((Object) null));
    }

    @Test
    void postRechazadoResponde409ConElMensaje() throws Exception {
        when(service.iniciar()).thenThrow(new PasadaRechazadaException("Ya hay una pasada en curso."));

        mockMvc.perform(post("/api/pasadas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya hay una pasada en curso."));
    }

    @Test
    void getActualResponde200ConLaPasada() throws Exception {
        when(service.estado()).thenReturn(Optional.of(pasada()));

        mockMvc.perform(get("/api/pasadas/actual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"));
    }

    @Test
    void getActualResponde204SiNuncaHuboUna() throws Exception {
        when(service.estado()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pasadas/actual"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void cancelarResponde200ConLaPasada() throws Exception {
        when(service.cancelar()).thenReturn(pasada());

        mockMvc.perform(post("/api/pasadas/actual/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"));
    }

    @Test
    void cancelarSinPasadaResponde409() throws Exception {
        when(service.cancelar()).thenThrow(new PasadaRechazadaException("No hay una pasada en curso."));

        mockMvc.perform(post("/api/pasadas/actual/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No hay una pasada en curso."));
    }
}
