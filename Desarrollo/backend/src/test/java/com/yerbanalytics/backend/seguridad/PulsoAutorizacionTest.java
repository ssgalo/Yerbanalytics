package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El pulso manual exige {@code pasadas.operar}, igual que la pasada y las secuencias (spec pulso-sensado). */
class PulsoAutorizacionTest extends SeguridadIntegracionBase {

    @Test
    void operarioNoPideLecturas_403ConElPermisoFaltante() throws Exception {
        Cookie operario = loginComo(Rol.OPERARIO);

        mvc.perform(post("/api/pulsos").cookie(operario).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ejecutarMotor\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permiso").value("pasadas.operar"));
    }
}
