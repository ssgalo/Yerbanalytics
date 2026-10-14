package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.OrigenPulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.Pulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulsoEnCursoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST del pulso manual: lo consume Demo Expo, así que las claves no se tocan. */
@WebMvcTest(PulsoController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PulsoController - contrato")
class PulsoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PulsoSensadoService service;

    @Test
    void leerAhoraPideUnPulsoSinMotorYResponde202() throws Exception {
        when(service.iniciar(OrigenPulso.MANUAL, false))
                .thenReturn(new Pulso("p-1", OrigenPulso.MANUAL, false, 1760446800000L));

        mockMvc.perform(post("/api/pulsos").contentType(MediaType.APPLICATION_JSON).content("{\"ejecutarMotor\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("p-1"))
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.ejecutaMotor").value(false))
                .andExpect(jsonPath("$.pedidoEn").value(1760446800000L));
    }

    @Test
    void conMotorPideUnPulsoConMotor() throws Exception {
        when(service.iniciar(OrigenPulso.MANUAL, true))
                .thenReturn(new Pulso("p-2", OrigenPulso.MANUAL, true, 1L));

        mockMvc.perform(post("/api/pulsos").contentType(MediaType.APPLICATION_JSON).content("{\"ejecutarMotor\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ejecutaMotor").value(true));
    }

    @Test
    void sinEjecutarMotorSeEjecutaElMotor() throws Exception {
        when(service.iniciar(OrigenPulso.MANUAL, true))
                .thenReturn(new Pulso("p-3", OrigenPulso.MANUAL, true, 1L));

        mockMvc.perform(post("/api/pulsos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/pulsos"))
                .andExpect(status().isAccepted());

        verify(service, org.mockito.Mockito.times(2)).iniciar(OrigenPulso.MANUAL, true);
    }

    @Test
    void conUnPulsoEnCursoResponde409ConElMotivo() throws Exception {
        when(service.iniciar(OrigenPulso.MANUAL, false))
                .thenThrow(new PulsoEnCursoException("Ya hay una lectura de sensores en curso."));

        mockMvc.perform(post("/api/pulsos").contentType(MediaType.APPLICATION_JSON).content("{\"ejecutarMotor\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya hay una lectura de sensores en curso."));
    }
}
