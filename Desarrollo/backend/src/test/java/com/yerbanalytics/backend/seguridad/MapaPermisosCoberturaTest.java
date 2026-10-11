package com.yerbanalytics.backend.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deny-by-default con red: recorre todos los {@code @RequestMapping} de la aplicación y falla si
 * alguno cae en el {@code denyAll()} final por no figurar en {@link MapaPermisos}. Al agregar un
 * endpoint, este test es el que avisa que falta declararle el permiso.
 */
@SpringBootTest
class MapaPermisosCoberturaTest {

    /** El contrato del dispositivo lo atiende otra cadena; {@code /error} es interno de Spring. */
    private static final List<String> FUERA_DE_LA_CADENA = List.of("/api/camara/v1/", "/error");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeos;

    @Test
    void todaRutaDeLaApiTienePermisoDeclarado() {
        List<String> sinPermiso = new ArrayList<>();
        int revisadas = 0;
        for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String patron : info.getPatternValues()) {
                if (FUERA_DE_LA_CADENA.stream().anyMatch(patron::startsWith)) {
                    continue;
                }
                String ruta = patron.replaceAll("\\{[^}]+}", "x");
                for (RequestMethod m : metodos.isEmpty() ? Set.of(RequestMethod.GET) : metodos) {
                    MockHttpServletRequest req = new MockHttpServletRequest(m.name(), ruta);
                    req.setServletPath(ruta);
                    revisadas++;
                    if (MapaPermisos.reglaPara(req).isEmpty()) {
                        sinPermiso.add(m + " " + patron);
                    }
                }
            }
        }
        assertThat(revisadas).isGreaterThan(30);
        assertThat(sinPermiso).as("Rutas sin permiso en MapaPermisos (quedan cerradas para todos)").isEmpty();
    }
}
