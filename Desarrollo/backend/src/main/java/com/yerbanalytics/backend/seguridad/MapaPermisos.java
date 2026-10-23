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

        // Criterio de diagnóstico: lo lee el servicio de inferencia (rol Servicio, con diagnosticos.ver y sin
        // configuracion.ver), así que la lectura va con el permiso de diagnósticos.
        permiso(r, get, "/api/configuracion/diagnostico", Permiso.DIAGNOSTICOS_VER);
        permiso(r, put, "/api/configuracion/diagnostico", Permiso.CONFIGURACION_EDITAR);

        permiso(r, get, "/api/configuracion", Permiso.CONFIGURACION_VER);
        permiso(r, put, "/api/configuracion", Permiso.CONFIGURACION_EDITAR);

        // Plan de nutrición de una macro-zona: el intervalo es configuración agronómica (el plan se lee del snapshot).
        permiso(r, put, "/api/zonas/*/plan-nutricion", Permiso.CONFIGURACION_EDITAR);

        // Plan de rustificación de una macro-zona: se ve en el detalle de la zona (como el snapshot) y se inicia o
        // elimina con permiso de configuración agronómica.
        permiso(r, get, "/api/zonas/*/rustificacion", Permiso.VIVERO_VER);
        permiso(r, post, "/api/zonas/*/rustificacion", Permiso.CONFIGURACION_EDITAR);
        permiso(r, post, "/api/zonas/*/rustificacion/cancelar", Permiso.CONFIGURACION_EDITAR);

        permiso(r, get, "/api/diagnosticos/**", Permiso.DIAGNOSTICOS_VER);
        permiso(r, post, "/api/diagnosticos", Permiso.DIAGNOSTICOS_REGISTRAR);

        permiso(r, get, "/api/historial/**", Permiso.HISTORIAL_VER);

        permiso(r, get, "/api/rules/**", Permiso.REGLAS_VER);
        permiso(r, put, "/api/rules/parametros", Permiso.REGLAS_EDITAR);

        permiso(r, get, "/api/hardware/**", Permiso.HARDWARE_VER);
        // La reparación va antes que el POST general: la registra quien arregla, no quien administra la flota.
        permiso(r, post, "/api/hardware/*/reparacion", Permiso.HARDWARE_REPARAR);
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
        // Secuencias de Demo Expo: misma pantalla que la pasada y nunca corren a la vez que ella.
        permiso(r, get, "/api/secuencias/**", Permiso.PASADAS_VER);
        permiso(r, post, "/api/secuencias", Permiso.PASADAS_OPERAR);
        permiso(r, post, "/api/secuencias/**", Permiso.PASADAS_OPERAR);
        // Pulso de sensado manual ("Leer ahora" / "Leer y ejecutar motor"): se opera desde Demo Expo.
        permiso(r, post, "/api/pulsos", Permiso.PASADAS_OPERAR);

        permiso(r, get, "/api/bloqueos", Permiso.BLOQUEOS_VER);
        permiso(r, post, "/api/bloqueos", Permiso.BLOQUEOS_GESTIONAR);
        permiso(r, HttpMethod.DELETE, "/api/bloqueos/**", Permiso.BLOQUEOS_GESTIONAR);

        // Tanques de insumo: el stock lo carga quien repone el producto.
        permiso(r, get, "/api/tanques", Permiso.INSUMOS_VER);
        permiso(r, post, "/api/tanques/**", Permiso.INSUMOS_GESTIONAR);
        // Revisión de un sector que alcanzó el tope de fitosanitarios (F-06): la hace quien maneja los insumos.
        permiso(r, post, "/api/sectores/*/revision-fitosanitario", Permiso.INSUMOS_GESTIONAR);
        // Revisión de una acción que quedó sin efectividad (S-06): la hace quien constata en persona qué pasó en el vivero.
        permiso(r, post, "/api/efectividad/*/revision", Permiso.EFECTIVIDAD_REVISAR);

        // Demo (solo existe cuando yerbanalytics.demo.enabled=true; en producción los controllers no se registran)
        permiso(r, null, "/api/demo/**", Permiso.SIMULACION_GESTIONAR);

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
