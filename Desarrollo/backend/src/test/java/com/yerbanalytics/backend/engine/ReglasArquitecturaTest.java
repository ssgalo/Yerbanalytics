package com.yerbanalytics.backend.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 4.10: los umbrales viven en el catálogo. Ninguna regla lee un {@code @Value} (salvo el
 * dato del lote de {@code ShadingRule}) y todas implementan la firma con {@code Evaluacion}.
 */
@DisplayName("Reglas del motor - arquitectura")
class ReglasArquitecturaTest {

    private static List<Class<?>> reglas() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Rule.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.yerbanalytics.backend.engine.rules")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }

    @Test
    void hayReglasParaVerificar() throws Exception {
        assertThat(reglas()).isNotEmpty();
    }

    @Test
    void ningunaReglaLeeUnValue() throws Exception {
        List<String> encontrados = new ArrayList<>();
        for (Class<?> c : reglas()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Value.class)) {
                    encontrados.add(c.getSimpleName() + "." + f.getName());
                }
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Parameter p : k.getParameters()) {
                    if (p.isAnnotationPresent(Value.class)) {
                        encontrados.add(c.getSimpleName() + "(" + p.getType().getSimpleName() + ")");
                    }
                }
            }
        }
        // La fecha de siembra de ShadingRule se fue con la rustificación global: ahora no queda ninguna excepción.
        assertThat(encontrados).isEmpty();
    }

    @Test
    void lasPrioridadesSonUnicasYElHardwareVaEntreElBloqueoYLaFrescura() throws Exception {
        Map<String, Integer> prioridades = new HashMap<>();
        for (Class<?> c : reglas()) {
            Field f = c.getDeclaredField("PRIORITY");
            f.setAccessible(true);
            prioridades.put(c.getSimpleName(), f.getInt(null));
        }

        // Un empate dejaría el orden de dos reglas librado al de la inyección de Spring.
        assertThat(prioridades.values()).as(prioridades.toString()).doesNotHaveDuplicates();
        assertThat(prioridades.get("ManualLockRule")).isLessThan(prioridades.get("IncompleteHardwareRule"));
        assertThat(prioridades.get("IncompleteHardwareRule")).isLessThan(prioridades.get("StaleSensorRule"));
    }

    @Test
    void lasReglasDeFallaVanAntesDeLasQueActuan() throws Exception {
        Map<String, Integer> prioridades = new HashMap<>();
        for (Class<?> c : reglas()) {
            Field f = c.getDeclaredField("PRIORITY");
            f.setAccessible(true);
            prioridades.put(c.getSimpleName(), f.getInt(null));
        }

        // R-02 y las demás reglas de riego no pueden ordenar agua antes de saber que la válvula está averiada.
        assertThat(prioridades.get("IncompleteHardwareRule")).isLessThan(prioridades.get("HydraulicFaultRule"));
        assertThat(prioridades.get("HydraulicFaultRule"))
                .isLessThan(prioridades.get("StaleSensorRule"))
                .isLessThan(prioridades.get("DeficitCriticoRule"))
                .isLessThan(prioridades.get("RiegoPorDeficitRule"))
                .isLessThan(prioridades.get("SupplyRule"));
        // La malla (reglas de zona) no se mueve sin antes saber que está averiada.
        assertThat(new com.yerbanalytics.backend.mediasombra.ShadeFaultRule().priority())
                .isLessThan(new com.yerbanalytics.backend.mediasombra.ShadingRule().priority());
    }

    @Test
    void todaReglaImplementaLaFirmaConEvaluacionYNingunaConservaLaVieja() throws Exception {
        for (Class<?> c : reglas()) {
            List<Method> evaluate = java.util.Arrays.stream(c.getDeclaredMethods())
                    .filter(m -> m.getName().equals("evaluate")).toList();
            assertThat(evaluate).as(c.getSimpleName())
                    .hasSize(1)
                    .allSatisfy(m -> assertThat(m.getParameterCount()).isEqualTo(2));
        }
    }
}
