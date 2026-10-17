package com.yerbanalytics.backend.engine.overrides;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DemoControllableRegistry")
class DemoControllableRegistryTest {

    @Test
    @DisplayName("lista las reglas por sector y las piezas que no son Rule, ordenadas por prioridad")
    void listaTodoOrdenadoPorPrioridad() {
        var registry = new DemoControllableRegistry(List.of(
                FakeControllable.of("ZoneShading", 50),
                FakeControllable.of("StaleSensorRule", 10),
                FakeControllable.of("ApplicationPreconditions", 30)));

        assertThat(registry.all()).extracting(DemoControllable::name)
                .containsExactly("StaleSensorRule", "ApplicationPreconditions", "ZoneShading");
    }

    @Test
    @DisplayName("a igual prioridad desempata por nombre, para que el listado sea estable")
    void desempataPorNombre() {
        var registry = new DemoControllableRegistry(List.of(
                FakeControllable.of("B", 10), FakeControllable.of("A", 10)));

        assertThat(registry.all()).extracting(DemoControllable::name).containsExactly("A", "B");
    }

    @Test
    @DisplayName("find devuelve la pieza por nombre, o vacío si no existe")
    void findPorNombre() {
        var registry = new DemoControllableRegistry(List.of(FakeControllable.of("ZoneShading", 50)));

        assertThat(registry.find("ZoneShading")).isPresent();
        assertThat(registry.find("NoExiste")).isEmpty();
    }

    @Test
    @DisplayName("dos piezas con el mismo nombre se rechazan al armar el registro")
    void nombreDuplicadoFallaAlArrancar() {
        List<DemoControllable> duplicadas = List.of(FakeControllable.of("X", 1), FakeControllable.of("X", 2));

        assertThatThrownBy(() -> new DemoControllableRegistry(duplicadas))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }
}
