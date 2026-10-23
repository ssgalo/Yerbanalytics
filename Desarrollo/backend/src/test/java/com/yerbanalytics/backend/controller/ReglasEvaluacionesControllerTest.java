package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.traza.AccionTrazada;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.zone.ZoneTraceStore;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 3.7: contrato HTTP de {@code GET /api/rules/evaluaciones/{sectorId}}. */
@WebMvcTest(ReglasEvaluacionesController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReglasEvaluacionesController")
class ReglasEvaluacionesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // El contrato HTTP se prueba contra lo que el store devuelve.
    @MockBean
    private TrazaEvaluacionStore store;

    @MockBean
    private SectorRepository sectorRepository;

    @MockBean
    private ZoneTraceStore zoneTraces;

    private static TrazaEvaluacion traza(OrigenEvaluacion origen, long epochMs) {
        TrazaRegla riego = new TrazaRegla("IrrigationRule", RuleBranch.RIEGO, 10, EstadoRegla.EVALUADA,
                List.of(new Comparacion("Humedad de sustrato", "riego.umbral-humedad", 38.0, Operador.LT, 42.0,
                        "%", true, ResultadoComparacion.CUMPLE)),
                List.of(new AccionTrazada(ActionType.ACTIVAR_VALVULA, "regar")), null);
        TrazaRegla omitida = new TrazaRegla("DailyVolumeLimitRule", RuleBranch.RIEGO, 6,
                EstadoRegla.OMITIDA_RAMA_BLOQUEADA, List.of(), List.of(), "WeatherOverrideRule");
        return new TrazaEvaluacion("MZ-2-006", "MZ-2", origen, Instant.ofEpochMilli(epochMs), "ab12",
                List.of(omitida, riego));
    }

    @Test
    void sectorEvaluado_devuelve200ConSectorZonaOrigenTimestampYReglas() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.masReciente("MZ-2-006")).thenReturn(java.util.Optional.of(traza(OrigenEvaluacion.TELEMETRIA, 1000)));

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorId").value("MZ-2-006"))
                .andExpect(jsonPath("$.zonaId").value("MZ-2"))
                .andExpect(jsonPath("$.origen").value("TELEMETRIA"))
                .andExpect(jsonPath("$.ts").exists())
                .andExpect(jsonPath("$.reglas.length()").value(2))
                .andExpect(jsonPath("$.reglas[0].ruleId").value("DailyVolumeLimitRule"))
                .andExpect(jsonPath("$.reglas[0].estado").value("OMITIDA_RAMA_BLOQUEADA"))
                .andExpect(jsonPath("$.reglas[0].bloqueadaPor").value("WeatherOverrideRule"))
                .andExpect(jsonPath("$.reglas[1].rama").value("RIEGO"))
                .andExpect(jsonPath("$.reglas[1].comparaciones[0].clave").value("riego.umbral-humedad"))
                .andExpect(jsonPath("$.reglas[1].comparaciones[0].recibido").value(38.0))
                .andExpect(jsonPath("$.reglas[1].comparaciones[0].operador").value("LT"))
                .andExpect(jsonPath("$.reglas[1].comparaciones[0].umbral").value(42.0))
                .andExpect(jsonPath("$.reglas[1].comparaciones[0].resultado").value("CUMPLE"))
                .andExpect(jsonPath("$.reglas[1].acciones[0].tipo").value("ACTIVAR_VALVULA"));
    }

    @Test
    void origenFiltraLaTraza() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.ultima("MZ-2-006", OrigenEvaluacion.TELEMETRIA))
                .thenReturn(java.util.Optional.of(traza(OrigenEvaluacion.TELEMETRIA, 2000)));

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006").param("origen", "TELEMETRIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("TELEMETRIA"));
    }

    @Test
    void origenSinTraza_devuelve204() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.ultima("MZ-2-006", OrigenEvaluacion.TELEMETRIA)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006").param("origen", "TELEMETRIA"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void sectorExistenteSinEvaluar_devuelve204() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.masReciente("MZ-2-006")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006"))
                .andExpect(status().isNoContent());
    }

    @Test
    void sectorInexistente_devuelve404() throws Exception {
        when(sectorRepository.existsById("NOPE")).thenReturn(false);

        mockMvc.perform(get("/api/rules/evaluaciones/NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void origenInvalido_devuelve400() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006").param("origen", "OTRO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laTrazaDelSectorTraeAlFinalLasReglasDeSuMacroZona() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.masReciente("MZ-2-006")).thenReturn(java.util.Optional.of(traza(OrigenEvaluacion.TELEMETRIA, 1000)));
        TrazaRegla plan = new TrazaRegla("NutritionPlanRule", RuleBranch.INSUMO, 23, EstadoRegla.EVALUADA,
                List.of(), List.of(new AccionTrazada(ActionType.UPDATE_NUTRITION_PLAN, "tanda iniciada")), null);
        when(zoneTraces.latest("MZ-2")).thenReturn(java.util.Optional.of(List.of(plan)));

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reglas.length()").value(3))
                .andExpect(jsonPath("$.reglas[2].ruleId").value("NutritionPlanRule"))
                .andExpect(jsonPath("$.reglas[2].rama").value("INSUMO"))
                .andExpect(jsonPath("$.reglas[2].acciones[0].tipo").value("UPDATE_NUTRITION_PLAN"));
    }

    @Test
    void siLaZonaTodaviaNoSeEvaluoLaTrazaQuedaTalCual() throws Exception {
        when(sectorRepository.existsById("MZ-2-006")).thenReturn(true);
        when(store.masReciente("MZ-2-006")).thenReturn(java.util.Optional.of(traza(OrigenEvaluacion.TELEMETRIA, 1000)));
        when(zoneTraces.latest("MZ-2")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/rules/evaluaciones/MZ-2-006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reglas.length()").value(2));
    }
}
