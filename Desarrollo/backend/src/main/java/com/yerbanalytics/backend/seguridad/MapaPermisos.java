package com.yerbanalytics.backend.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <strong>Único lugar</strong> donde se declara qué permiso exige cada ruta de la API de
 * plataforma (design D5). {@link SeguridadConfig} arma la autorización a partir de esta tabla y
 * termina con {@code denyAll()}: una ruta nueva que no figure acá queda cerrada para todos los
 * roles hasta que se le asigne un permiso. {@code MapaPermisosCoberturaTest} recorre todos los
 * {@code @RequestMapping} y falla si alguno quedó sin declarar.
 *
 * <p>Gana la primera regla que coincide, así que las rutas específicas van antes que las
 * generales ({@code /api/configuracion/demo-expo} antes que {@code /api/configuracion}).
 *
 * <p>{@code /api/camara/v1/**} no figura: es el contrato del dispositivo y lo atiende otra cadena,
 * con su propio token.
 */
public final class MapaPermisos {

    /** Qué exige una ruta. */
    public sealed interface Acceso {
        /** Sin sesión: el login y la CA local. */
        record Publico() implements Acceso {}

        /** Cualquier usuario con sesión, sin permiso específico (su propia sesión y su clave). */
        record Sesion() implements Acceso {}

        record ConPermiso(Permiso permiso) implements Acceso {}
    }

    public record Regla(HttpMethod metodo, String patron, Acceso acceso) {
        public AntPathRequestMatcher matcher() {
            return new AntPathRequestMatcher(patron, metodo != null ? metodo.name() : null);
        }
    }

    private static final List<Regla> REGLAS = construir();

    private MapaPermisos() {
    }

    public static List<Regla> reglas() {
        return REGLAS;
    }

    /** La primera regla que coincide con la petición, o vacío si cae en el {@code denyAll()} final. */
    public static Optional<Regla> reglaPara(HttpServletRequest req) {
        return REGLAS.stream().filter(r -> r.matcher().matches(req)).findFirst();
    }

    public static boolean esPublica(HttpServletRequest req) {
        return reglaPara(req).map(r -> r.acceso() instanceof Acceso.Publico).orElse(false);
    }

    private static List<Regla> construir() {
        List<Regla> r = new ArrayList<>();
        HttpMethod get = HttpMethod.GET;
        HttpMethod post = HttpMethod.POST;
        HttpMethod put = HttpMethod.PUT;

        // Sin sesión
        publico(r, post, "/api/auth/login");
        publico(r, get, "/ca.pem");

        // Sólo sesión: lo propio de cada usuario
        sesion(r, get, "/api/auth/perfil");
        sesion(r, post, "/api/auth/actividad");
        sesion(r, post, "/api/auth/logout");
        sesion(r, put, "/api/auth/clave");

        // Vivero y Demo Expo. El GET del interruptor queda en vivero.ver: el sidebar lo necesita
        // para saber si mostrar la pestaña.
        permiso(r, get, "/api/nursery/**", Permiso.VIVERO_VER);
        permiso(r, get, "/api/configuracion/demo-expo", Permiso.VIVERO_VER);
        permiso(r, put, "/api/configuracion/demo-expo", Permiso.DEMO_EXPO_CONFIGURAR);

        permiso(r, get, "/api/configuracion", Permiso.CONFIGURACION_VER);
        permiso(r, put, "/api/configuracion", Permiso.CONFIGURACION_EDITAR);

        permiso(r, get, "/api/diagnosticos/**", Permiso.DIAGNOSTICOS_VER);
        permiso(r, post, "/api/diagnosticos", Permiso.DIAGNOSTICOS_REGISTRAR);

        permiso(r, get, "/api/historial/**", Permiso.HISTORIAL_VER);

        permiso(r, get, "/api/rules/**", Permiso.REGLAS_VER);
        permiso(r, put, "/api/rules/parametros", Permiso.REGLAS_EDITAR);

        permiso(r, get, "/api/hardware/**", Permiso.HARDWARE_VER);
        permiso(r, post, "/api/hardware/**", Permiso.HARDWARE_GESTIONAR);
        permiso(r, put, "/api/hardware/**", Permiso.HARDWARE_GESTIONAR);

        permiso(r, get, "/api/topologia/**", Permiso.TOPOLOGIA_VER);
        permiso(r, post, "/api/topologia/**", Permiso.TOPOLOGIA_GESTIONAR);
        permiso(r, put, "/api/topologia/**", Permiso.TOPOLOGIA_GESTIONAR);

        // Plataforma de captura (no el contrato del dispositivo)
        permiso(r, get, "/api/capturas/**", Permiso.CAPTURAS_VER);
        permiso(r, post, "/api/capturas/ordenes", Permiso.CAPTURAS_ORDENAR);
        permiso(r, null, "/api/camara/vinculacion", Permiso.CAMARA_GESTIONAR);
        permiso(r, null, "/api/camara/dispositivos/**", Permiso.CAMARA_GESTIONAR);

        permiso(r, get, "/api/pasadas/**", Permiso.PASADAS_VER);
        permiso(r, post, "/api/pasadas/**", Permiso.PASADAS_OPERAR);

        // Seguridad
        permiso(r, null, "/api/usuarios/**", Permiso.USUARIOS_GESTIONAR);
        permiso(r, null, "/api/roles/**", Permiso.USUARIOS_GESTIONAR);
        permiso(r, null, "/api/seguridad/politica", Permiso.USUARIOS_GESTIONAR);
        permiso(r, get, "/api/auditoria/**", Permiso.AUDITORIA_VER);

        return List.copyOf(r);
    }

    private static void publico(List<Regla> r, HttpMethod m, String patron) {
        r.add(new Regla(m, patron, new Acceso.Publico()));
    }

    private static void sesion(List<Regla> r, HttpMethod m, String patron) {
        r.add(new Regla(m, patron, new Acceso.Sesion()));
    }

    private static void permiso(List<Regla> r, HttpMethod m, String patron, Permiso p) {
        r.add(new Regla(m, patron, new Acceso.ConPermiso(p)));
    }
}
