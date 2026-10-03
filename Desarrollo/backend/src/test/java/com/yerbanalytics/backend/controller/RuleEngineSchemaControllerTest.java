package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tarea 2.5: {@code GET /api/rules/schema} suma las claves de parámetros de cada nodo. */
@WebMvcTest(RuleEngineSchemaController.class)
@DisplayName("GET /api/rules/schema")
class RuleEngineSchemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleOrchestrator orchestrator;

    private static Rule regla(String nombre, int prioridad, RuleBranch rama, DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return prioridad; }
            @Override public String name() { return nombre; }
            @Override public RuleBranch branch() { return rama; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx) { return List.of(); }
        };
    }

    @Test
    void cadaNodoDeReglaTraeSusParametros_yLosEspecialesUnaListaVacia() throws Exception {
        when(orchestrator.getRules()).thenReturn(List.of(
                regla("ManualLockRule", 1, RuleBranch.GLOBAL),
                regla("IrrigationRule", 10, RuleBranch.RIEGO,
                        ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.TIEMPO_MAX_APERTURA)));

        mockMvc.perform(get("/api/rules/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id=='IrrigationRule')].parametros[*]",
                        contains("riego.umbral-humedad", "riego.tiempo-max-apertura")))
                .andExpect(jsonPath("$.nodes[?(@.id=='ManualLockRule')].parametros[*]", empty()))
                // Los nodos especiales siempre traen el campo, aunque vacío.
                .andExpect(jsonPath("$.nodes[?(@.id=='start')].parametros", hasSize(1)))
                .andExpect(jsonPath("$.nodes[?(@.id=='start')].parametros[*]", empty()))
                .andExpect(jsonPath("$.nodes[?(@.id=='abort-GLOBAL')].parametros[*]", empty()))
                .andExpect(jsonPath("$.nodes[?(@.id=='success-RIEGO')].parametros[*]", empty()));
    }
}
