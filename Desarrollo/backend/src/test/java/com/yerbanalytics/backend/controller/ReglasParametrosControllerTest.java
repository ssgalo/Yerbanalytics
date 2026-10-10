package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.dto.ParametroDto;
import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.parametros.CambioParametro;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ErrorParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosInvalidosException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 2.4: contrato HTTP de {@code /api/rules/parametros}. */
@WebMvcTest(ReglasParametrosController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReglasParametrosController")
class ReglasParametrosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogoParametrosService service;

    private static CatalogoReglasDto catalogo(String valorUmbral) {
        ParametroDto umbral = new ParametroDto("riego.umbral-humedad", "Umbral de riego", "desc", "RIEGO",
                "NUMERO", "%", valorUmbral, "42", 35.0, 60.0, 0, "reglas_v2 §5 R-01",
                !valorUmbral.equals("42"), List.of("IrrigationRule", "WeatherOverrideRule"), null, null);
        return new CatalogoReglasDto(
                List.of(new ReglaCatalogoDto("IrrigationRule", "Riego", "RIEGO", 10, List.of("riego.umbral-humedad")),
                        new ReglaCatalogoDto("WeatherOverrideRule", "Lluvia", "RIEGO", 5, List.of("riego.umbral-humedad"))),
                List.of(umbral));
    }

    @Test
    void get_devuelveElCatalogoNormalizado() throws Exception {
        when(service.catalogo()).thenReturn(catalogo("42"));

        mockMvc.perform(get("/api/rules/parametros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.length()").value(1))
                .andExpect(jsonPath("$.parametros[0].clave").value("riego.umbral-humedad"))
                .andExpect(jsonPath("$.parametros[0].usadoPor.length()").value(2))
                .andExpect(jsonPath("$.parametros[0].valor").value("42"))
                .andExpect(jsonPath("$.parametros[0].fabrica").value("42"))
                .andExpect(jsonPath("$.parametros[0].modificado").value(false))
                .andExpect(jsonPath("$.parametros[0].min").value(35.0))
                .andExpect(jsonPath("$.reglas[0].id").value("IrrigationRule"))
                .andExpect(jsonPath("$.reglas[0].parametros[0]").value("riego.umbral-humedad"));
    }

    @Test
    void put_validoDevuelve200ConElCatalogoYPasaElUsuario() throws Exception {
        when(service.guardar(any(), eq("Ana"))).thenReturn(catalogo("40"));

        mockMvc.perform(put("/api/rules/parametros")
                        .header("X-Usuario", "Ana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cambios\":[{\"clave\":\"riego.umbral-humedad\",\"valor\":\"40\"},"
                                + "{\"clave\":\"riego.tiempo-max-apertura\",\"valor\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros[0].valor").value("40"))
                .andExpect(jsonPath("$.parametros[0].modificado").value(true));

        verify(service).guardar(List.of(
                new CambioParametro("riego.umbral-humedad", "40"),
                new CambioParametro("riego.tiempo-max-apertura", null)), "Ana");
    }

    @Test
    void put_invalidoDevuelve400ConErroresPorClave() throws Exception {
        when(service.guardar(any(), any())).thenThrow(new ParametrosInvalidosException(List.of(
                new ErrorParametro("riego.umbral-humedad", "Debe estar entre 35 y 60 %."))));

        mockMvc.perform(put("/api/rules/parametros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cambios\":[{\"clave\":\"riego.umbral-humedad\",\"valor\":\"70\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].clave").value("riego.umbral-humedad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Debe estar entre 35 y 60 %."));
    }

    @Test
    void put_sinCuerpoDevuelve400() throws Exception {
        mockMvc.perform(put("/api/rules/parametros").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
