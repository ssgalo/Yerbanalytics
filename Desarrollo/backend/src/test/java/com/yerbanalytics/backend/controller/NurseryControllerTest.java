package com.yerbanalytics.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NurseryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getNursery_devuelveSnapshotCompleto() throws Exception {
        mockMvc.perform(get("/api/nursery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas").isArray())
                .andExpect(jsonPath("$.sectors").isArray())
                .andExpect(jsonPath("$.stats.total").value(600))
                .andExpect(jsonPath("$.weather.tempC").value(21));
    }
}
