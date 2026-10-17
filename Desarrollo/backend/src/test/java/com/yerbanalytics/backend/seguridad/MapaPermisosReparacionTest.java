package com.yerbanalytics.backend.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** S-05: registrar una reparación exige {@code hardware.reparar}, no {@code hardware.gestionar}. Sin base: es puro código. */
@DisplayName("MapaPermisos - reparación de hardware")
class MapaPermisosReparacionTest {

    private static Permiso permisoDe(String metodo, String ruta) {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
        req.setServletPath(ruta);
        return MapaPermisos.reglaPara(req)
                .map(r -> ((MapaPermisos.Acceso.ConPermiso) r.acceso()).permiso())
                .orElseThrow(() -> new AssertionError("la ruta quedó sin permiso: " + metodo + " " + ruta));
    }

    @Test
    @DisplayName("POST /api/hardware/{id}/reparacion exige hardware.reparar")
    void laReparacionExigeSuPermiso() {
        assertThat(permisoDe("POST", "/api/hardware/DEV-101/reparacion")).isEqualTo(Permiso.HARDWARE_REPARAR);
    }

    @Test
    @DisplayName("el alta, el recambio y la lectura del hardware siguen con sus permisos")
    void elRestoNoCambia() {
        assertThat(permisoDe("POST", "/api/hardware")).isEqualTo(Permiso.HARDWARE_GESTIONAR);
        assertThat(permisoDe("PUT", "/api/hardware/DEV-101")).isEqualTo(Permiso.HARDWARE_GESTIONAR);
        assertThat(permisoDe("GET", "/api/hardware")).isEqualTo(Permiso.HARDWARE_VER);
    }
}
