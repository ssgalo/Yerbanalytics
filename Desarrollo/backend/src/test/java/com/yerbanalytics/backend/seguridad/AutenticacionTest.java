package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-01: login, mensaje genérico, inactividad, logout y cambio de contraseña. */
class AutenticacionTest extends SeguridadIntegracionBase {

    // ------------------------------------------------------------------
    // Login (CA-01 / CA-02)
    // ------------------------------------------------------------------

    @Test
    void loginValido_fijaCookieSeguraYDevuelveElPerfilSinLaClave() throws Exception {
        UsuarioEntity ana = crearUsuario("ana", Rol.INGENIERO_AGRONOMO);

        MvcResult r = loginCrudo(ana.getUsername(), CLAVE);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String setCookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(CookieSesion.NOMBRE + "=").contains("HttpOnly")
                .contains("SameSite=Strict").contains("Path=/api");
        JsonNode perfil = cuerpo(r);
        assertThat(perfil.get("username").asText()).isEqualTo(ana.getUsername());
        assertThat(perfil.get("rol").asText()).isEqualTo("INGENIERO_AGRONOMO");
        assertThat(perfil.get("rolNombre").asText()).isEqualTo("Ingeniero Agrónomo");
        assertThat(perfil.get("permisos").toString()).contains("reglas.editar").doesNotContain("auditoria.ver");
        assertThat(perfil.get("inactividadMin").asInt()).isPositive();
        assertThat(perfil.get("debeCambiarClave").asBoolean()).isFalse();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("clave").doesNotContain("$2a$");
        assertThat(usuarios.findById(ana.getId()).orElseThrow().getUltimoIngreso()).isNotNull();
    }

    @Test
    void todasLasFallasDeLoginResponden401Identico() throws Exception {
        UsuarioEntity activo = crearUsuario("activo", Rol.OPERARIO);
        UsuarioEntity suspendido = crearUsuario("susp", Rol.OPERARIO, EstadoUsuario.SUSPENDIDO, false);
        UsuarioEntity baja = crearUsuario("baja", Rol.OPERARIO, EstadoUsuario.BAJA, false);

        List<MvcResult> fallas = List.of(
                loginCrudo(activo.getUsername(), "clave-equivocada"),
                loginCrudo("no-existe-nadie-asi", CLAVE),
                loginCrudo(suspendido.getUsername(), CLAVE),
                loginCrudo(baja.getUsername(), CLAVE));

        for (MvcResult r : fallas) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentAsString()).isEqualTo("{\"error\":\"Credenciales incorrectas\"}");
            assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    void elLoginNoDistingueMayusculasEnElUsuario() throws Exception {
        UsuarioEntity u = crearUsuario("mayus", Rol.OPERARIO);
        assertThat(loginCrudo(u.getUsername().toUpperCase(), CLAVE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unaCookieViejaNoImpideVolverAIniciarSesion() throws Exception {
        UsuarioEntity u = crearUsuario("vieja", Rol.OPERARIO);
        Cookie c = login(u);
        mvc.perform(post("/api/auth/logout").cookie(c)).andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", u.getUsername(), "clave", CLAVE))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Inactividad (CA-03)
    // ------------------------------------------------------------------

    @Test
    void sesionInactivaMasDelMaximo_respondeSesionExpiradaYQuedaCerrada() throws Exception {
        Cookie c = loginComo(Rol.OPERARIO);
        SesionEntity s = sesionDe(c);
        s.setUltimaActividad(Instant.now().minus(61, ChronoUnit.MINUTES));
        sesiones.saveAndFlush(s);

        mvc.perform(get("/api/auth/perfil").cookie(c))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_EXPIRADA"));

        assertThat(sesionDe(c).getMotivoCierre()).isEqualTo(MotivoCierre.EXPIRADA);
        // Y sigue cerrada aunque la actividad vuelva a estar "en fecha".
        mvc.perform(get("/api/auth/perfil").cookie(c))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_EXPIRADA"));
    }

    @Test
    void unGetNoRenuevaLaActividad_yLaSenalDeActividadSi() throws Exception {
        Cookie c = loginComo(Rol.OPERARIO);
        Instant hace10 = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        SesionEntity s = sesionDe(c);
        s.setUltimaActividad(hace10);
        sesiones.saveAndFlush(s);

        mvc.perform(get("/api/auth/perfil").cookie(c)).andExpect(status().isOk());
        mvc.perform(get("/api/historial").cookie(c)).andExpect(status().isOk());
        sesiones.flush();
        assertThat(sesionDe(c).getUltimaActividad()).isEqualTo(hace10);

        mvc.perform(post("/api/auth/actividad").cookie(c)).andExpect(status().isNoContent());
        sesiones.flush();
        assertThat(sesionDe(c).getUltimaActividad()).isAfter(hace10.plus(9, ChronoUnit.MINUTES));
    }

    // ------------------------------------------------------------------
    // Logout
    // ------------------------------------------------------------------

    @Test
    void logoutInvalidaLaSesionYBorraLaCookie() throws Exception {
        Cookie c = loginComo(Rol.OPERARIO);

        MvcResult r = mvc.perform(post("/api/auth/logout").cookie(c)).andExpect(status().isNoContent()).andReturn();
        assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        mvc.perform(get("/api/auth/perfil").cookie(c)).andExpect(status().isUnauthorized());
        assertThat(sesionDe(c).getMotivoCierre()).isEqualTo(MotivoCierre.LOGOUT);
    }

    @Test
    void sinCookieResponde401SinSesion() throws Exception {
        mvc.perform(get("/api/nursery"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SIN_SESION"));
    }

    // ------------------------------------------------------------------
    // Contraseñas
    // ------------------------------------------------------------------

    @Test
    void conClaveTemporalSoloPuedeCambiarla() throws Exception {
        UsuarioEntity u = crearUsuario("temporal", Rol.OPERARIO, EstadoUsuario.ACTIVO, true);
        Cookie c = login(u);

        mvc.perform(get("/api/historial").cookie(c))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("CAMBIO_CLAVE_REQUERIDO"));
        mvc.perform(get("/api/auth/perfil").cookie(c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(true));

        cambiarClave(c, CLAVE, "una-clave-nueva").andExpect(status().isNoContent());

        mvc.perform(get("/api/historial").cookie(c)).andExpect(status().isOk());
    }

    @Test
    void cambioDeClavePropio_cierraLasDemasSesionesYConservaLaActual() throws Exception {
        UsuarioEntity u = crearUsuario("doble", Rol.OPERARIO);
        Cookie primera = login(u);
        Cookie segunda = login(u);

        cambiarClave(primera, CLAVE, "otra-clave-larga").andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/perfil").cookie(primera)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/perfil").cookie(segunda))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_REVOCADA"));
        assertThat(loginCrudo(u.getUsername(), "otra-clave-larga").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void claveActualIncorrectaOClaveDebil_400SinCambios() throws Exception {
        UsuarioEntity u = crearUsuario("debil", Rol.OPERARIO);
        Cookie c = login(u);

        cambiarClave(c, "no-es-esta", "una-clave-nueva").andExpect(status().isBadRequest());
        cambiarClave(c, CLAVE, "corta").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("8")));
        cambiarClave(c, CLAVE, u.getUsername()).andExpect(status().isBadRequest());

        assertThat(loginCrudo(u.getUsername(), CLAVE).getResponse().getStatus()).isEqualTo(200);
    }

    private org.springframework.test.web.servlet.ResultActions cambiarClave(Cookie c, String actual, String nueva)
            throws Exception {
        return mvc.perform(put("/api/auth/clave").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("actual", actual, "nueva", nueva))));
    }
}
