package com.yerbanalytics.backend.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Pone la seguridad en condiciones al arrancar, en este orden:
 *
 * <ol>
 *   <li>Instala los triggers que hacen append-only la auditoría (design D7). {@code ddl-auto} no
 *       crea triggers y {@code spring.sql.init} está apagado, así que lo hace la app, después de
 *       que Hibernate creó la tabla. No es una migración manual más: se aplica sola.</li>
 *   <li>Siembra la matriz por defecto si la tabla está vacía, y la fila de la política de sesión.</li>
 *   <li>Crea el Administrador inicial si no hay ninguno activo (design D9).</li>
 * </ol>
 */
@Component
@Order(0)
public class SeguridadInicializador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeguridadInicializador.class);

    private static final String SCRIPT_TRIGGERS = "auditoria-triggers.sql";

    private final JdbcTemplate jdbc;
    private final RolPermisoService matriz;
    private final PoliticaSesionService politica;
    private final UsuarioService usuarios;
    private final String adminUsuario;
    private final String adminClave;

    public SeguridadInicializador(JdbcTemplate jdbc, RolPermisoService matriz, PoliticaSesionService politica,
                                  UsuarioService usuarios,
                                  @Value("${yerbanalytics.auth.admin-inicial.usuario:admin}") String adminUsuario,
                                  @Value("${yerbanalytics.auth.admin-inicial.clave:}") String adminClave) {
        this.jdbc = jdbc;
        this.matriz = matriz;
        this.politica = politica;
        this.usuarios = usuarios;
        this.adminUsuario = adminUsuario;
        this.adminClave = adminClave;
    }

    @Override
    public void run(ApplicationArguments args) {
        instalarTriggersAuditoria();
        if (matriz.sembrarSiVacia()) {
            log.info("Matriz de permisos sembrada con los valores por defecto");
        }
        politica.asegurarFila();
        crearAdministradorInicial();
    }

    void instalarTriggersAuditoria() {
        String sql;
        try {
            sql = new ClassPathResource(SCRIPT_TRIGGERS).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se encontró " + SCRIPT_TRIGGERS + " en el classpath", e);
        }
        try {
            // Una sola sentencia JDBC: el driver de PostgreSQL respeta el $$ del cuerpo de la función,
            // cosa que el separador por ';' de ScriptUtils no haría.
            jdbc.execute(sql);
        } catch (DataAccessException e) {
            throw new IllegalStateException("No se pudieron instalar los triggers que protegen la auditoría de "
                    + "seguridad. Si el usuario de la base no puede crear funciones, corré "
                    + "src/main/resources/" + SCRIPT_TRIGGERS + " a mano con un usuario que pueda "
                    + "(ver README del backend).", e);
        }
    }

    private void crearAdministradorInicial() {
        boolean generada = adminClave == null || adminClave.isBlank();
        String clave = generada ? claveAleatoria() : adminClave;
        if (!generada) {
            ReglasClave.validar(adminUsuario, clave);
        }
        usuarios.crearAdministradorInicialSiFalta(adminUsuario, clave).ifPresent(u -> {
            if (generada) {
                // Única vez que esta contraseña existe en claro. Va al log a propósito (design D9).
                log.warn("""

                        ================================================================
                          Administrador inicial creado
                            usuario: {}
                            contraseña temporal: {}
                          No se configuró YERBANALYTICS_ADMIN_CLAVE: esta contraseña se
                          muestra UNA sola vez. Se pide cambiarla en el primer ingreso.
                        ================================================================""",
                        u.username(), clave);
            } else {
                log.warn("Administrador inicial '{}' creado con la contraseña configurada; se pide cambiarla "
                        + "en el primer ingreso", u.username());
            }
        });
    }

    private static String claveAleatoria() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
