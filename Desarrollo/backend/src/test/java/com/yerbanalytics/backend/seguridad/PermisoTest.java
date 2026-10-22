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
                "vivero.ver", "diagnosticos.ver", "historial.ver", "capturas.ver", "pasadas.ver",
                "bloqueos.ver", "bloqueos.gestionar", "hardware.ver", "hardware.reparar");
        assertThat(codigos(Rol.SERVICIO)).containsExactlyInAnyOrder("vivero.ver", "topologia.ver", "capturas.ver",
                "capturas.ordenar", "diagnosticos.ver", "diagnosticos.registrar", "camara.gestionar",
                "bloqueos.ver", "bloqueos.gestionar", "simulacion.gestionar", "hardware.ver", "hardware.reparar",
                "reglas.ver", "pasadas.ver", "pasadas.operar");
        assertThat(codigos(Rol.INGENIERO_AGRONOMO)).contains("configuracion.editar", "reglas.editar", "bloqueos.ver", "bloqueos.gestionar",
                        "hardware.reparar")
                .doesNotContain("auditoria.ver", "pasadas.operar", "usuarios.gestionar");
        assertThat(codigos(Rol.PRODUCTOR_VIVERISTA)).contains("pasadas.operar", "topologia.ver", "bloqueos.ver", "hardware.ver")
                .doesNotContain("auditoria.ver", "reglas.editar", "bloqueos.gestionar", "hardware.reparar");
    }

    @Test
    void elRolServicioPuedeLeerLasReglasYOperarElPulsoSinEditarNada() {
        // Las cuentas de integración leen el catálogo y la traza del motor y piden pulsos, secuencias y pasadas.
        assertThat(codigos(Rol.SERVICIO)).contains("reglas.ver", "pasadas.ver", "pasadas.operar")
                .doesNotContain("reglas.editar", "configuracion.editar", "usuarios.gestionar");
    }

    @Test
    void elRolServicioSoloLeeElHardwareNuncaLoGestiona() {
        // El simulador consulta qué sectores tienen su electroválvula registrada (escenario S-04); no registra ni recambia nada.
        assertThat(codigos(Rol.SERVICIO)).contains("hardware.ver").doesNotContain("hardware.gestionar");
    }

    @Test
    void quienRegistraReparacionesPuedeVerElHardwarePeroNoRegistrarloNiRecambiarlo() {
        // S-05: el Operario es quien está en el vivero cuando algo se rompe. Ve el panel para repararlo, pero el alta
        // y el recambio de dispositivos (hardware.gestionar) siguen siendo de quien administra.
        for (Rol rol : new Rol[]{Rol.OPERARIO, Rol.INGENIERO_AGRONOMO, Rol.SERVICIO}) {
            assertThat(codigos(rol)).as(rol.name()).contains("hardware.ver", "hardware.reparar")
                    .doesNotContain("hardware.gestionar");
        }
        assertThat(Permiso.HARDWARE_REPARAR.getLectura()).isEqualTo(Permiso.HARDWARE_VER);
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
