package com.yerbanalytics.backend.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuerpos JSON de los {@code 401} y {@code 403} de la cadena de seguridad. Son los que el
 * dashboard decodifica para decidir qué aviso mostrar, así que su forma es parte de la API.
 */
final class RespuestasSeguridad {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RespuestasSeguridad() {
    }

    static void sinSesion(HttpServletResponse res, SesionService.MotivoRechazo motivo) throws IOException {
        String mensaje = switch (motivo) {
            case SIN_SESION -> "Iniciá sesión para continuar.";
            case SESION_EXPIRADA -> "Tu sesión se cerró por inactividad.";
            case SESION_REVOCADA -> "Tu cuenta cambió; volvé a iniciar sesión.";
        };
        escribir(res, HttpServletResponse.SC_UNAUTHORIZED, Map.of("error", mensaje, "motivo", motivo.name()));
    }

    static void sinPermiso(HttpServletResponse res, Permiso permiso) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", permiso != null
                ? "No tenés permiso para esta acción."
                : "Esta ruta no tiene un permiso asignado: está cerrada para todos los roles.");
        if (permiso != null) {
            cuerpo.put("permiso", permiso.getCodigo());
        }
        escribir(res, HttpServletResponse.SC_FORBIDDEN, cuerpo);
    }

    static void cambioClaveRequerido(HttpServletResponse res) throws IOException {
        escribir(res, HttpServletResponse.SC_FORBIDDEN, Map.of(
                "error", "Tenés que cambiar la contraseña temporal antes de seguir.",
                "motivo", "CAMBIO_CLAVE_REQUERIDO"));
    }

    private static void escribir(HttpServletResponse res, int status, Map<String, ?> cuerpo) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(JSON.writeValueAsString(cuerpo));
    }
}
