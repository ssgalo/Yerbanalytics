package com.yerbanalytics.backend.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Catálogo y matriz por defecto (control-acceso-roles). Sin base: es puro código. */
class PermisoTest {

    @Test
    void laMatrizPorDefectoEsLaDeLaSpec() {
        assertThat(Permiso.matrizPorDefecto(Rol.ADMINISTRADOR)).containsExactlyInAnyOrder(Permiso.values());
        assertThat(codigos(Rol.OPERARIO)).containsExactlyInAnyOrder(
                "vivero.ver", "diagnosticos.ver", "historial.ver", "capturas.ver", "pasadas.ver");
        assertThat(codigos(Rol.SERVICIO)).containsExactlyInAnyOrder("vivero.ver", "topologia.ver", "capturas.ver",
                "capturas.ordenar", "diagnosticos.ver", "diagnosticos.registrar", "camara.gestionar");
        assertThat(codigos(Rol.INGENIERO_AGRONOMO)).contains("configuracion.editar", "reglas.editar")
                .doesNotContain("auditoria.ver", "pasadas.operar", "usuarios.gestionar");
        assertThat(codigos(Rol.PRODUCTOR_VIVERISTA)).contains("pasadas.operar", "topologia.ver")
                .doesNotContain("auditoria.ver", "reglas.editar");
    }

    @Test
    void laMatrizPorDefectoRespetaLosParesDeLectura() {
        for (Rol rol : Rol.values()) {
            Set<Permiso> m = Permiso.matrizPorDefecto(rol);
            m.stream().filter(p -> p.getLectura() != null)
                    .forEach(p -> assertThat(m).as(rol + " / " + p).contains(p.getLectura()));
        }
    }

    @Test
    void losCodigosSonUnicosYResolubles() {
        assertThat(Arrays.stream(Permiso.values()).map(Permiso::getCodigo).distinct()).hasSize(Permiso.values().length);
        for (Permiso p : Permiso.values()) {
            assertThat(Permiso.porCodigo(p.getCodigo())).contains(p);
        }
    }

    @Test
    void conTodosLosPermisosCubreElCatalogo() {
        WithMockUser anotacion = ConTodosLosPermisos.class.getAnnotation(WithMockUser.class);
        assertThat(anotacion.authorities()).containsExactlyInAnyOrder(
                Arrays.stream(Permiso.values()).map(Permiso::getCodigo).toArray(String[]::new));
    }

    private static Set<String> codigos(Rol rol) {
        return EnumSet.copyOf(Permiso.matrizPorDefecto(rol)).stream().map(Permiso::getCodigo).collect(Collectors.toSet());
    }
}
