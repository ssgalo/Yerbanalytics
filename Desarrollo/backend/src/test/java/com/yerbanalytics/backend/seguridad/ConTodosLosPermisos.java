package com.yerbanalytics.backend.seguridad;

import org.springframework.security.test.context.support.WithMockUser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Para los tests de integración que ejercitan funcionalidad, no permisos: la petición entra como
 * un usuario simulado con todo el catálogo. Los permisos se prueban en {@code seguridad/}.
 * {@code PermisoTest} verifica que esta lista siga cubriendo el catálogo completo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithMockUser(username = "test", authorities = {
        "vivero.ver", "diagnosticos.ver", "diagnosticos.registrar", "historial.ver",
        "configuracion.ver", "configuracion.editar", "reglas.ver", "reglas.editar",
        "hardware.ver", "hardware.gestionar", "topologia.ver", "topologia.gestionar",
        "capturas.ver", "capturas.ordenar", "camara.gestionar", "pasadas.ver", "pasadas.operar",
        "demo-expo.configurar", "usuarios.gestionar", "auditoria.ver"})
public @interface ConTodosLosPermisos {
}
