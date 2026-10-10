package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaSeguridadRepository;
import com.yerbanalytics.backend.seguridad.auditoria.TipoAuditoria;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-20 CA-01/CA-02: ABM de usuarios, salvaguardas y matriz editable. */
class GestionUsuariosTest extends SeguridadIntegracionBase {

    @Autowired private AuditoriaSeguridadRepository auditoria;

    // ------------------------------------------------------------------
    // Revocación de sesiones (CA-02)
    // ------------------------------------------------------------------

    @Test
    void cambioDeRolConSesionAbierta_laSiguientePeticionRecibeSesionRevocada() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity productor = crearUsuario("productor", Rol.PRODUCTOR_VIVERISTA);
        Cookie suya = login(productor);

        mvc.perform(put("/api/usuarios/" + productor.getId()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", productor.getNombre(), "rol", "OPERARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("OPERARIO"));

        mvc.perform(get("/api/historial").cookie(suya))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_REVOCADA"));

        // Al reingresar opera con el rol nuevo.
        Cookie nueva = login(productor);
        mvc.perform(get("/api/topologia").cookie(nueva)).andExpect(status().isForbidden());

        assertThat(auditoria.findAllByOrderByIdAsc()).anySatisfy(a -> {
            assertThat(a.getTipo()).isEqualTo(TipoAuditoria.USUARIO_ROL_CAMBIADO);
            assertThat(a.getObjetivoRef()).isEqualTo(productor.getUsername());
            assertThat(a.getDetalle()).contains("PRODUCTOR_VIVERISTA").contains("OPERARIO");
        });
    }

    @Test
    void editarSoloElNombre_noCortaLasSesiones() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity u = crearUsuario("nombre", Rol.OPERARIO);
        Cookie suya = login(u);

        mvc.perform(put("/api/usuarios/" + u.getId()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "Nombre Corregido", "rol", "OPERARIO"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/perfil").cookie(suya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Corregido"));
    }

    @Test
    void suspenderConSesionAbierta_revocaEImpideElLogin_yReactivarLoDevuelve() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity u = crearUsuario("suspendible", Rol.OPERARIO);
        Cookie suya = login(u);

        accion(admin, u, "suspender").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SUSPENDIDO"));

        mvc.perform(get("/api/historial").cookie(suya))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_REVOCADA"));
        assertThat(loginCrudo(u.getUsername(), CLAVE).getResponse().getStatus()).isEqualTo(401);

        accion(admin, u, "reactivar").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));
        assertThat(loginCrudo(u.getUsername(), CLAVE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void bajaConSesionAbierta_revoca_yNoSePuedeReactivar() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity u = crearUsuario("baja", Rol.OPERARIO);
        Cookie suya = login(u);

        accion(admin, u, "baja").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BAJA"));
        mvc.perform(get("/api/historial").cookie(suya))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_REVOCADA"));
        accion(admin, u, "reactivar").andExpect(status().isConflict());
    }

    @Test
    void blanqueo_revocaYObligaACambiarLaClave_sinDejarlaEnLaAuditoria() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity u = crearUsuario("blanqueo", Rol.OPERARIO);
        Cookie suya = login(u);

        mvc.perform(put("/api/usuarios/" + u.getId() + "/clave").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"temporal-secreta-123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/historial").cookie(suya)).andExpect(status().isUnauthorized());
        Cookie nueva = new Cookie(CookieSesion.NOMBRE, loginCrudo(u.getUsername(), "temporal-secreta-123")
                .getResponse().getCookie(CookieSesion.NOMBRE).getValue());
        mvc.perform(get("/api/historial").cookie(nueva))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("CAMBIO_CLAVE_REQUERIDO"));
        assertThat(auditoria.findAllByOrderByIdAsc())
                .anySatisfy(a -> assertThat(a.getTipo()).isEqualTo(TipoAuditoria.USUARIO_CLAVE_BLANQUEADA))
                .allSatisfy(a -> assertThat(a.getDetalle()).doesNotContain("temporal-secreta-123"));
    }

    // ------------------------------------------------------------------
    // Alta y validaciones
    // ------------------------------------------------------------------

    @Test
    void altaValida_quedaActivaConClaveTemporalYAuditada() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        String username = "jperez-" + System.nanoTime() % 100000;

        JsonNode creado = cuerpo(alta(admin, username.toUpperCase(), "Juan Pérez", "OPERARIO", "temporal-123")
                .andExpect(status().isCreated()).andReturn());

        assertThat(creado.get("username").asText()).isEqualTo(username);
        assertThat(creado.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(creado.get("debeCambiarClave").asBoolean()).isTrue();
        assertThat(creado.toString()).doesNotContain("clave\"").doesNotContain("$2a$");
        assertThat(auditoria.findAllByOrderByIdAsc()).anySatisfy(a -> {
            assertThat(a.getTipo()).isEqualTo(TipoAuditoria.USUARIO_ALTA);
            assertThat(a.getObjetivoRef()).isEqualTo(username);
        });
    }

    @Test
    void usernameRepetido_409AunqueElOtroEsteDadoDeBaja() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity dadoDeBaja = crearUsuario("repetido", Rol.OPERARIO, EstadoUsuario.BAJA, false);

        alta(admin, dadoDeBaja.getUsername().toUpperCase(), "Otro", "OPERARIO", "temporal-123")
                .andExpect(status().isConflict());
    }

    @Test
    void validacionesDeAlta_400() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        alta(admin, "con espacios", "X", "OPERARIO", "temporal-123").andExpect(status().isBadRequest());
        alta(admin, "rol-raro-" + System.nanoTime() % 1000, "X", "SUPERVISOR", "temporal-123")
                .andExpect(status().isBadRequest());
        alta(admin, "clave-corta-" + System.nanoTime() % 1000, "X", "OPERARIO", "corta")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Salvaguardas
    // ------------------------------------------------------------------

    @Test
    void autosuspensionYAutobaja_409() throws Exception {
        UsuarioEntity yo = crearUsuario("yo", Rol.ADMINISTRADOR);
        Cookie c = login(yo);
        accion(c, yo, "suspender").andExpect(status().isConflict());
        accion(c, yo, "baja").andExpect(status().isConflict());
    }

    @Test
    void ultimoAdministradorActivo_409() throws Exception {
        UsuarioEntity unico = crearUsuario("unico", Rol.ADMINISTRADOR);
        Cookie c = login(unico);
        // Dentro de la transacción del test (se revierte): todos los demás administradores suspendidos.
        List<UsuarioEntity> otros = new ArrayList<>(usuarios.findAll().stream()
                .filter(u -> u.getRol() == Rol.ADMINISTRADOR && !u.getId().equals(unico.getId())).toList());
        otros.forEach(u -> u.setEstado(EstadoUsuario.SUSPENDIDO));
        usuarios.saveAllAndFlush(otros);

        mvc.perform(put("/api/usuarios/" + unico.getId()).cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", unico.getNombre(), "rol", "OPERARIO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("único Administrador")));
    }

    // ------------------------------------------------------------------
    // Matriz de permisos
    // ------------------------------------------------------------------

    @Test
    void matrizConEdicionSinLectura_400() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        guardarMatriz(admin, "OPERARIO", List.of("vivero.ver", "reglas.editar")).andExpect(status().isBadRequest());
    }

    @Test
    void administradorSinGestionDeUsuarios_409() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        guardarMatriz(admin, "ADMINISTRADOR", List.of("vivero.ver", "auditoria.ver")).andExpect(status().isConflict());
    }

    @Test
    void cambioDeMatriz_revocaLasSesionesDelRol_rigeEnSeguida_yQuedaAuditado() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        Cookie operario = loginComo(Rol.OPERARIO);
        Instant antes = Instant.now();

        guardarMatriz(admin, "OPERARIO", List.of("vivero.ver", "historial.ver", "reglas.ver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos.length()").value(3));

        mvc.perform(get("/api/historial").cookie(operario))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").value("SESION_REVOCADA"));
        Cookie otraVez = loginComo(Rol.OPERARIO);
        mvc.perform(get("/api/rules/schema").cookie(otraVez)).andExpect(status().isOk());
        mvc.perform(get("/api/diagnosticos").cookie(otraVez)).andExpect(status().isForbidden());

        assertThat(auditoria.findAllByOrderByIdAsc()).anySatisfy(a -> {
            assertThat(a.getTipo()).isEqualTo(TipoAuditoria.ROL_PERMISOS_CAMBIADOS);
            assertThat(a.getOcurridoEn()).isAfterOrEqualTo(antes.minusSeconds(1));
            assertThat(a.getDetalle()).contains("\"agregados\":[\"reglas.ver\"]");
        });
    }

    @Test
    void elAutorDelCambioDeMatrizSigueOperando() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        List<String> todos = java.util.Arrays.stream(Permiso.values()).map(Permiso::getCodigo).toList();
        List<String> sinDemoExpo = todos.stream().filter(p -> !p.equals("demo-expo.configurar")).toList();

        guardarMatriz(admin, "ADMINISTRADOR", sinDemoExpo).andExpect(status().isOk());

        mvc.perform(get("/api/usuarios").cookie(admin)).andExpect(status().isOk());
        mvc.perform(put("/api/configuracion/demo-expo").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elCatalogoVieneConGrupoDescripcionYParDeLectura() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/roles/permisos").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Permiso.values().length))
                .andExpect(jsonPath("$[?(@.codigo=='reglas.editar')].lectura").value("reglas.ver"))
                .andExpect(jsonPath("$[0].grupo").isNotEmpty());
        mvc.perform(get("/api/roles").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].intocables.length()").value(2));
    }

    @Test
    void politicaDeSesion_rangoYAuditoria() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        politica(admin, 2).andExpect(status().isBadRequest());
        politica(admin, 500).andExpect(status().isBadRequest());
        politica(admin, 15).andExpect(status().isOk()).andExpect(jsonPath("$.inactividadMin").value(15));
        assertThat(auditoria.findAllByOrderByIdAsc())
                .anySatisfy(a -> assertThat(a.getTipo()).isEqualTo(TipoAuditoria.POLITICA_SESION_CAMBIADA));
    }

    // ------------------------------------------------------------------

    private ResultActions accion(Cookie c, UsuarioEntity u, String accion) throws Exception {
        return mvc.perform(post("/api/usuarios/" + u.getId() + "/" + accion).cookie(c));
    }

    private ResultActions alta(Cookie c, String username, String nombre, String rol, String clave) throws Exception {
        return mvc.perform(post("/api/usuarios").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "nombre", nombre, "rol", rol,
                        "clave", clave))));
    }

    private ResultActions guardarMatriz(Cookie c, String rol, List<String> permisos) throws Exception {
        return mvc.perform(put("/api/roles/" + rol + "/permisos").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("permisos", permisos))));
    }

    private ResultActions politica(Cookie c, int minutos) throws Exception {
        return mvc.perform(put("/api/seguridad/politica").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inactividadMin\":" + minutos + "}"));
    }
}
