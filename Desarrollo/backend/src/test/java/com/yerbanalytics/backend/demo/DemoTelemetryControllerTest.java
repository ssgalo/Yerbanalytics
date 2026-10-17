package com.yerbanalytics.backend.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("DemoTelemetryController")
class DemoTelemetryControllerTest {

    @Nested
    @WebMvcTest(DemoTelemetryController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(DemoTelemetrySourceStore.class)
    @TestPropertySource(properties = "yerbanalytics.demo.enabled=true")
    class ModoDemo {

        @Autowired
        MockMvc mvc;

        @Autowired
        DemoTelemetrySourceStore store;

        @BeforeEach
        void reset() {
            store.set(TelemetrySource.REAL_NODE);
        }

        @Test
        void porDefectoEsElNodoReal() throws Exception {
            mvc.perform(get("/api/demo/telemetry-source"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("REAL_NODE"));
        }

        @Test
        void cambiaLaFuenteYLaCompuertaLaSigue() throws Exception {
            mvc.perform(put("/api/demo/telemetry-source").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"source\":\"SIMULATOR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("SIMULATOR"));

            assertThat(store.get()).isEqualTo(TelemetrySource.SIMULATOR);
            assertThat(store.admits("nursery/demo/zone/MZ-1/telemetry")).isTrue();
        }

        @Test
        void unValorInvalidoEsUn400() throws Exception {
            mvc.perform(put("/api/demo/telemetry-source").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"source\":\"OTRO\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/demo/telemetry-source").contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @WebMvcTest(DemoTelemetryController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(DemoTelemetrySourceStore.class)
    @TestPropertySource(properties = "yerbanalytics.demo.enabled=false")
    class SinModoDemo {

        @Autowired
        MockMvc mvc;

        @Test
        void sinModoDemoNoExisteElEndpoint() throws Exception {
            mvc.perform(get("/api/demo/telemetry-source")).andExpect(status().isNotFound());
            mvc.perform(put("/api/demo/telemetry-source").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"source\":\"SIMULATOR\"}")).andExpect(status().isNotFound());
        }
    }
}
