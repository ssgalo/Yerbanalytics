package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaSeguridadRepository;
import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-20 CA-03: registro append-only, encadenado y consultable. */
class AuditoriaTest extends SeguridadIntegracionBase {

    @Autowired private AuditoriaSeguridadRepository repo;
    @Autowired private AuditoriaService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @Test
    void unCambioRechazadoNoSeAudita() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        long antes = repo.count();

        mvc.perform(post("/api/usuarios").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"nombre\":\"X\",\"rol\":\"OPERARIO\",\"clave\":\"temporal-123\"}"))
                .andExpect(status().isBadRequest());

        assertThat(repo.count()).isEqualTo(antes);
    }

    @Test
    void laBaseRechazaUpdate() {
        // Un test por sentencia: la primera que la base rechaza aborta la transacción.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE auditoria_seguridad SET objetivo_ref = 'alterado' WHERE id = (SELECT max(id) FROM auditoria_seguridad)"))
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void laBaseRechazaDelete() {
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM auditoria_seguridad WHERE id = (SELECT max(id) FROM auditoria_seguridad)"))
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void laBaseRechazaTruncate() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE auditoria_seguridad")).rootCause().hasMessageContaining("append-only");
    }

    @Test
    void laVerificacionDetectaUnRegistroAlteradoPorFuera() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        // Garantiza al menos un registro escrito por este test.
        mvc.perform(post("/api/usuarios/" + crearUsuario("victima", Rol.OPERARIO).getId() + "/suspender").cookie(admin))
                .andExpect(status().isOk());
        assertThat(service.verificar().integra()).as("la cadena de la base de desarrollo ya venía rota").isTrue();

        Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria_seguridad", Long.class);
        // Alguien con acceso directo desactiva la protección y cambia un registro (todo se revierte al final).
        jdbc.execute("ALTER TABLE auditoria_seguridad DISABLE TRIGGER auditoria_seguridad_sin_update_delete");
        jdbc.update("UPDATE auditoria_seguridad SET objetivo_ref = 'otro' WHERE id = ?", ultimo);
        // Fuera del test cada petición tiene su propio contexto de persistencia; acá hay que vaciarlo
        // para que la verificación lea la fila alterada y no la copia que ya tenía cargada.
        em.clear();

        mvc.perform(get("/api/auditoria/verificacion").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(false))
                .andExpect(jsonPath("$.primerIdRoto").value(ultimo));
    }

    @Test
    void consultaFiltradaPorObjetivo_delMasRecienteAlMasAntiguo() throws Exception {
        Cookie admin = loginComo(Rol.ADMINISTRADOR);
        UsuarioEntity jperez = crearUsuario("jperez", Rol.OPERARIO);
        mvc.perform(post("/api/usuarios/" + jperez.getId() + "/suspender").cookie(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/usuarios/" + jperez.getId() + "/reactivar").cookie(admin)).andExpect(status().isOk());
        crearUsuario("ruido", Rol.OPERARIO);

        mvc.perform(get("/api/auditoria").param("objetivo", jperez.getUsername().toUpperCase()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].tipo").value("USUARIO_REACTIVADO"))
                .andExpect(jsonPath("$.items[1].tipo").value("USUARIO_SUSPENDIDO"))
                .andExpect(jsonPath("$.items[0].objetivoRef").value(jperez.getUsername()))
                .andExpect(jsonPath("$.items[0].detalle.nuevo.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.items[0].ocurridoEn").isString());

        mvc.perform(get("/api/auditoria").param("tipo", "NO_EXISTE").cookie(admin)).andExpect(status().isBadRequest());
    }
}
