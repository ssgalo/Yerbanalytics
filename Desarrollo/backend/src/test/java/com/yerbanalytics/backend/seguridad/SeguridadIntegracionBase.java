package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración de seguridad. Requiere PostgreSQL levantado, como el resto de
 * los {@code @SpringBootTest} del repo.
 *
 * <p>Cada test corre en una transacción que se revierte al final: MockMvc atiende la petición en
 * el mismo hilo, así que los servicios se suman a ella. Usuarios, sesiones y registros de
 * auditoría creados por un test no quedan en la base (la reversión no dispara los triggers de la
 * auditoría: no es un {@code DELETE}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class SeguridadIntegracionBase {

    protected static final String CLAVE = "clave-de-prueba";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected SesionRepository sesiones;
    @Autowired protected PasswordEncoder encoder;

    /** Usuario activo con contraseña ya definitiva. El sufijo evita chocar con datos de la base. */
    protected UsuarioEntity crearUsuario(String prefijo, Rol rol) {
        return crearUsuario(prefijo, rol, EstadoUsuario.ACTIVO, false);
    }

    protected UsuarioEntity crearUsuario(String prefijo, Rol rol, EstadoUsuario estado, boolean debeCambiarClave) {
        UsuarioEntity u = new UsuarioEntity();
        u.setUsername((prefijo + "-" + UUID.randomUUID().toString().substring(0, 8)).toLowerCase());
        u.setNombre("Test " + prefijo);
        u.setRol(rol);
        u.setEstado(estado);
        u.setClaveHash(encoder.encode(CLAVE));
        u.setDebeCambiarClave(debeCambiarClave);
        u.setCreadoEn(Instant.now());
        return usuarios.saveAndFlush(u);
    }

    protected MvcResult loginCrudo(String username, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "clave", clave))))
                .andReturn();
    }

    /** Inicia sesión y devuelve la cookie. */
    protected Cookie login(UsuarioEntity u) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", u.getUsername(), "clave", CLAVE))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie c = r.getResponse().getCookie(CookieSesion.NOMBRE);
        assertNotNull(c, "el login no fijó la cookie de sesión");
        return new Cookie(c.getName(), c.getValue());
    }

    protected Cookie loginComo(Rol rol) throws Exception {
        return login(crearUsuario(rol.name().toLowerCase().replace('_', '-'), rol));
    }

    protected JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected SesionEntity sesionDe(Cookie c) {
        return sesiones.findById(Hashes.sha256(c.getValue())).orElseThrow();
    }
}
