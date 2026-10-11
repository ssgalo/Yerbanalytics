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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pone la seguridad en condiciones al arrancar, en este orden:
 *
 * <ol>
 *   <li>Instala los triggers que hacen append-only la auditoría (design D7). {@code ddl-auto} no
 *       crea triggers y {@code spring.sql.init} está apagado, así que lo hace la app, después de
 *       que Hibernate creó la tabla. No es una migración manual más: se aplica sola.</li>
 *   <li>Siembra la matriz por defecto si la tabla está vacía, y la fila de la política de sesión.</li>
 *   <li>Crea el Administrador inicial si no hay ninguno activo (design D9).</li>
 *   <li>Crea las cuentas declaradas en {@code yerbanalytics.auth.usuarios-iniciales} que no
 *       existan. Así un clon nuevo arranca con las cuentas de servicio que el simulador y la
 *       inferencia esperan en su {@code .env}, sin pasar por el dashboard. El backend no sabe
 *       para qué es cada una: es una lista de configuración, no código.</li>
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
    private final String usuariosIniciales;

    public SeguridadInicializador(JdbcTemplate jdbc, RolPermisoService matriz, PoliticaSesionService politica,
                                  UsuarioService usuarios,
                                  @Value("${yerbanalytics.auth.admin-inicial.usuario:admin}") String adminUsuario,
                                  @Value("${yerbanalytics.auth.admin-inicial.clave:}") String adminClave,
                                  @Value("${yerbanalytics.auth.usuarios-iniciales:}") String usuariosIniciales) {
        this.jdbc = jdbc;
        this.matriz = matriz;
        this.politica = politica;
        this.usuarios = usuarios;
        this.adminUsuario = adminUsuario;
        this.adminClave = adminClave;
        this.usuariosIniciales = usuariosIniciales;
    }

    @Override
    public void run(ApplicationArguments args) {
        instalarTriggersAuditoria();
        if (matriz.sembrarSiVacia()) {
            log.info("Matriz de permisos sembrada con los valores por defecto");
        }
        politica.asegurarFila();
        crearAdministradorInicial();
        crearUsuariosIniciales();
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
        if (usuarios.hayAdministradorActivo()) {
            return;
        }
        // Sin clave configurada no se inventa una: la única fuente del Administrador inicial es el
        // .env (design D9). Frenar el arranque es preferible a una contraseña que nadie conoce.
        if (adminClave == null || adminClave.isBlank()) {
            throw new IllegalStateException("No hay ningún Administrador activo y YERBANALYTICS_ADMIN_CLAVE no "
                    + "está definida. Copiá Desarrollo/backend/env.example a .env (o definí la variable) "
                    + "y volvé a arrancar.");
        }
        ReglasClave.validar(adminUsuario, adminClave);
        usuarios.crearAdministradorInicialSiFalta(adminUsuario, adminClave).ifPresent(u ->
                log.warn("Administrador inicial '{}' creado con la contraseña configurada; se pide cambiarla "
                        + "en el primer ingreso", u.username()));
    }

    /** Una entrada de {@code yerbanalytics.auth.usuarios-iniciales}. */
    record UsuarioInicial(String username, Rol rol, String clave) {}

    private void crearUsuariosIniciales() {
        for (UsuarioInicial ui : parsearUsuariosIniciales(usuariosIniciales)) {
            usuarios.crearUsuarioInicialSiFalta(ui.username(), ui.rol(), ui.clave()).ifPresent(u ->
                    log.info("Usuario inicial '{}' creado con rol {}", u.username(), u.rol().getNombre()));
        }
    }

    /**
     * Formato: {@code usuario:ROL:clave} separados por coma, p. ej.
     * {@code simulador:SERVICIO:una-clave,ana:OPERARIO:otra-clave}. La clave es lo que sigue al
     * segundo ':' (puede contener ':'), pero no comas. Una entrada mal formada frena el arranque:
     * es preferible a descubrir después que una integración no puede entrar.
     */
    static List<UsuarioInicial> parsearUsuariosIniciales(String valor) {
        List<UsuarioInicial> lista = new ArrayList<>();
        if (valor == null || valor.isBlank()) {
            return lista;
        }
        for (String entrada : valor.split(",")) {
            if (entrada.isBlank()) {
                continue;
            }
            String[] partes = entrada.trim().split(":", 3);
            if (partes.length != 3 || partes[0].isBlank() || partes[2].isEmpty()) {
                throw new IllegalStateException("yerbanalytics.auth.usuarios-iniciales: la entrada '"
                        + partes[0] + ":…' no respeta el formato usuario:ROL:clave");
            }
            Rol rol;
            try {
                rol = Rol.valueOf(partes[1].trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("yerbanalytics.auth.usuarios-iniciales: el rol '" + partes[1]
                        + "' de '" + partes[0] + "' no existe (usá " + Arrays.toString(Rol.values()) + ")");
            }
            lista.add(new UsuarioInicial(partes[0].trim(), rol, partes[2]));
        }
        return lista;
    }
}
