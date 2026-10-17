package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla de oro del control desde el simulador: toda pieza de decisión registrada como bean es un
 * {@link DemoControllable} y se presenta con una etiqueta propia, no con su nombre técnico.
 */
@DisplayName("DemoControllable - arquitectura")
class DemoControllableArquitecturaTest {

    private static List<Class<?>> componentes(Class<?> tipo) throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(tipo));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.yerbanalytics.backend")) {
            Class<?> clase = Class.forName(bd.getBeanClassName());
            if (!esDeTest(clase)) {
                out.add(clase);
            }
        }
        return out;
    }

    /** Los dobles de prueba también están en el classpath; la regla vale para el código de producción. */
    private static boolean esDeTest(Class<?> clase) {
        return clase.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes");
    }

    @Test
    void hayPiezasParaVerificar() throws Exception {
        assertThat(componentes(DemoControllable.class)).isNotEmpty();
    }

    @Test
    @DisplayName("toda Rule es un DemoControllable")
    void todaReglaEsControlable() throws Exception {
        assertThat(componentes(Rule.class)).allSatisfy(c ->
                assertThat(DemoControllable.class).isAssignableFrom(c));
    }

    @Test
    @DisplayName("todo DemoControllable concreto declara su propio label()")
    void todoControlableDeclaraLabel() throws Exception {
        assertThat(componentes(DemoControllable.class)).allSatisfy(c ->
                assertThat(c.getMethod("label").getDeclaringClass())
                        .as("%s debe sobreescribir label()", c.getSimpleName())
                        .isNotEqualTo(DemoControllable.class)
                        .isNotEqualTo(Rule.class));
    }
}
