package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.DemoRuleControlService;
import com.yerbanalytics.backend.engine.overrides.EffectiveRuleOverrides;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleStateEntity;
import com.yerbanalytics.backend.engine.overrides.RuleStateRepository;
import com.yerbanalytics.backend.engine.overrides.FakeControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rutas {@code /api/demo/rules}: existen sólo con el modo demo y cubren cualquier {@link DemoControllable}. */
@DisplayName("DemoRulesController")
class DemoRulesControllerTest {

    @TestConfiguration
    static class Piezas {
        @Bean
        DemoControllable sombra() {
            return FakeControllable.of("ZoneShading", 50);
        }

        @Bean
        DemoControllable regla() {
            return FakeControllable.of("StaleSensorRule", 10);
        }

        /** En el vivero, "StaleSensorRule" está apagada; "ZoneShading" no tiene fila (rige la siembra: prendida). */
        @Bean
        RuleStateRepository reglaEstadoRepository() {
            RuleStateRepository repository = org.mockito.Mockito.mock(RuleStateRepository.class);
            org.mockito.Mockito.when(repository.findAll())
                    .thenReturn(java.util.List.of(new RuleStateEntity("StaleSensorRule", false, "recalibrando", "Ana", 1L)));
            return repository;
        }
    }

    @Nested
    @WebMvcTest(DemoRulesController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import({Piezas.class, DemoControllableRegistry.class, DemoRuleControlService.class, RuleOverridesStore.class,
            OperationalRuleStates.class, EffectiveRuleOverrides.class})
    @TestPropertySource(properties = "yerbanalytics.demo.enabled=true")
    class ModoDemo {

        @Autowired
        MockMvc mvc;

        @Autowired
        RuleOverridesStore store;

        @BeforeEach
        void sinOverrides() {
            store.clear();
        }

        @Test
        void listaTambienLasPiezasQueNoSonRegla() throws Exception {
            mvc.perform(get("/api/demo/rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("StaleSensorRule"))
                    .andExpect(jsonPath("$[1].name").value("ZoneShading"))
                    .andExpect(jsonPath("$[1].cooldownKeys[0]").value("interval"))
                    .andExpect(jsonPath("$[1].enabled").value(true));
        }

        @Test
        void apagaUnaPiezaPorZona() throws Exception {
            mvc.perform(put("/api/demo/rules/ZoneShading")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));

            mvc.perform(get("/api/demo/rules"))
                    .andExpect(jsonPath("$[1].enabled").value(false));
        }

        @Test
        void informaElEstadoOperativoYElEfectivoDeCadaPieza() throws Exception {
            mvc.perform(get("/api/demo/rules"))
                    .andExpect(jsonPath("$[0].name").value("StaleSensorRule"))
                    .andExpect(jsonPath("$[0].operationalActive").value(false))
                    .andExpect(jsonPath("$[0].effectiveEnabled").value(false))
                    .andExpect(jsonPath("$[1].operationalActive").value(true))
                    .andExpect(jsonPath("$[1].effectiveEnabled").value(true));
        }

        @Test
        void prenderUnaApagadaEnElViveroLaHaceCorrerYElOperativoSigueApagado() throws Exception {
            mvc.perform(put("/api/demo/rules/StaleSensorRule")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.operationalActive").value(false))
                    .andExpect(jsonPath("$.effectiveEnabled").value(true));
        }

        @Test
        void limpiarLaCapaDeDemoDevuelveElEstadoOperativo() throws Exception {
            mvc.perform(put("/api/demo/rules/StaleSensorRule")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"));

            store.clear();

            mvc.perform(get("/api/demo/rules"))
                    .andExpect(jsonPath("$[0].effectiveEnabled").value(false))
                    .andExpect(jsonPath("$[0].enabled").value(true));
        }

        @Test
        void nombreDesconocidoEs404() throws Exception {
            mvc.perform(put("/api/demo/rules/NoExiste")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void claveNoDeclaradaEs400() throws Exception {
            mvc.perform(put("/api/demo/rules/ZoneShading")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"cooldownMinutes\":{\"otra\":1}}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/demo/rules/ZoneShading")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"bypassed\":[\"otra\"]}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void cooldownsACeroAlcanzaATodos() throws Exception {
            mvc.perform(put("/api/demo/rules/cooldowns/zero"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].cooldownMinutes.interval").value(0.0))
                    .andExpect(jsonPath("$[1].cooldownMinutes.interval").value(0.0));
        }
    }

    @Nested
    @WebMvcTest(DemoRulesController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @TestPropertySource(properties = "yerbanalytics.demo.enabled=false")
    class ModoReal {

        @Autowired
        MockMvc mvc;

        @Test
        void lasRutasNoExisten() throws Exception {
            mvc.perform(get("/api/demo/rules")).andExpect(status().isNotFound());
            mvc.perform(put("/api/demo/rules/cooldowns/zero")).andExpect(status().isNotFound());
        }
    }
}
