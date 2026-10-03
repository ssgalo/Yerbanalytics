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
import java.util.List;

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
    void ningunaReglaLeeUnValueSalvoElDatoDelLoteDeShadingRule() throws Exception {
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
        assertThat(encontrados).containsExactly("ShadingRule(String)");
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
