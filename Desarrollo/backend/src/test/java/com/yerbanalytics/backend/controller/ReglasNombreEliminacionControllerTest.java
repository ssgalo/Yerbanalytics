package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoControllableRegistry;
import com.yerbanalytics.backend.engine.overrides.OperationalRuleStates;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.overrides.RuleStateEntity;
import com.yerbanalytics.backend.engine.overrides.RuleStateRepository;
import com.yerbanalytics.backend.engine.parametros.ReglasCatalogo;
import com.yerbanalytics.backend.engine.parametros.ReglasEstadoService;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * rediseno-motor-reglas D-5: {@code PUT /api/rules/{name}/etiqueta}, {@code DELETE /api/rules/{name}} y
 * {@code POST /api/rules/{name}/restaurar}, con el estado operativo real (sin transacción: el cache se actualiza en el
 * acto).
 */
@WebMvcTest(ReglasEstadoController.class)
// Test del controller, sin la cadena de seguridad: los permisos se prueban en seguridad/.
@AutoConfigureMockMvc(addFilters = false)
@Import({ReglasNombreEliminacionControllerTest.Piezas.class, DemoControllableRegistry.class,
        OperationalRuleStates.class, RuleOverridesStore.class, ReglasCatalogo.class, ReglasEstadoService.class})
@DisplayName("Nombre propio, eliminar y restaurar una regla")
class ReglasNombreEliminacionControllerTest {

    private record Pieza(String name, String label, String code, RuleEffect effect, Optional<String> protection)
            implements DemoControllable {
        @Override public RuleBranch branch() { return RuleBranch.RIEGO; }
        @Override public int priority() { return name.equals("Accion") ? 10 : 5; }
    }

    @TestConfiguration
    static class Piezas {
        @Bean
        DemoControllable accion() {
            return new Pieza("Accion", "Riego por déficit hídrico", "R-01", RuleEffect.ACTS, Optional.empty());
        }

        @Bean
        DemoControllable proteccion() {
            return new Pieza("Proteccion", "Sustrato saturado", "R-04", RuleEffect.PROTECTS,
                    Optional.of("Se riega con el sustrato saturado."));
        }

        @Bean
        RuleStateRepository reglaEstadoRepository() {
            RuleStateRepository repository = mock(RuleStateRepository.class);
            when(repository.findAll()).thenReturn(List.of());
            return repository;
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RuleStateRepository repository;
    @Autowired
    private OperationalRuleStates states;

    @MockBean
    private HistorialService historial;

    @BeforeEach
    void limpiar() {
        reset(repository, historial);
        when(repository.findAll()).thenReturn(List.of());
        states.reload();
    }

    private ResultActions etiqueta(String nombre, String cuerpo) throws Exception {
        return mvc.perform(put("/api/rules/" + nombre + "/etiqueta").header("X-Usuario", "Ana López")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions eliminar(String nombre) throws Exception {
        return mvc.perform(delete("/api/rules/" + nombre).header("X-Usuario", "Ana López"));
    }

    private ResultActions restaurar(String nombre) throws Exception {
        return mvc.perform(post("/api/rules/" + nombre + "/restaurar").header("X-Usuario", "Ana López"));
    }

    // ------------------------------------------------------------------ nombre propio

    @Test
    @DisplayName("renombrar: label es el nombre nuevo y etiquetaFabrica el de fábrica; el interruptor no cambia")
    void renombrar() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"  Riego normal  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Riego normal"))
                .andExpect(jsonPath("$.etiquetaFabrica").value("Riego por déficit hídrico"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.eliminada").value(false));

        ArgumentCaptor<RuleStateEntity> fila = ArgumentCaptor.forClass(RuleStateEntity.class);
        verify(repository).save(fila.capture());
        assertThat(fila.getValue().getEtiqueta()).isEqualTo("Riego normal");
        assertThat(fila.getValue().getUpdatedTs()).as("renombrar no decide el interruptor").isNull();
        verify(historial).registrarNombreRegla("Ana López", "Accion", "R-01", "Riego por déficit hídrico",
                "Riego normal", false);
        assertThat(states.isActive("Accion")).isTrue();
    }

    @Test
    @DisplayName("etiqueta vacía o null restablece el de fábrica")
    void restablecer() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"Riego normal\"}").andExpect(status().isOk());

        etiqueta("Accion", "{\"etiqueta\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Riego por déficit hídrico"));
        verify(historial).registrarNombreRegla("Ana López", "Accion", "R-01", "Riego normal",
                "Riego por déficit hídrico", true);

        etiqueta("Accion", "{\"etiqueta\":\"Otro\"}").andExpect(status().isOk());
        etiqueta("Accion", "{\"etiqueta\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Riego por déficit hídrico"));
    }

    @Test
    @DisplayName("más de 80 caracteres después de recortar: 400 ETIQUETA_DEMASIADO_LARGA; 80 justos entran")
    void etiquetaDemasiadoLarga() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"" + "a".repeat(81) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ETIQUETA_DEMASIADO_LARGA"));
        verify(repository, never()).save(any());

        etiqueta("Accion", "{\"etiqueta\":\" " + "a".repeat(80) + " \"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("el mismo nombre que ya tiene: 200 sin fila ni evento")
    void mismoNombre() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"Riego por déficit hídrico\"}").andExpect(status().isOk());

        verify(repository, never()).save(any());
        verify(historial, never()).registrarNombreRegla(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean());
    }

    @Test
    @DisplayName("renombrar una pieza desconocida: 404")
    void renombrarDesconocida() throws Exception {
        etiqueta("NoExiste", "{\"etiqueta\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el nombre propio se conserva al apagar y prender")
    void nombreSobreviveAlInterruptor() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"Riego normal\"}").andExpect(status().isOk());

        mvc.perform(put("/api/rules/Accion/estado").contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Riego normal"))
                .andExpect(jsonPath("$.activa").value(false));
    }

    // ------------------------------------------------------------------ eliminar y restaurar

    @Test
    @DisplayName("eliminar: queda apagada y eliminada, el motor la omite y queda en el historial")
    void eliminar() throws Exception {
        eliminar("Accion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("Accion"))
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.eliminada").value(true));

        assertThat(states.isActive("Accion")).isFalse();
        assertThat(states.inactiveNames()).contains("Accion");
        verify(historial).registrarEliminacionRegla("Ana López", "Accion", "Riego por déficit hídrico", "R-01", true);
    }

    @Test
    @DisplayName("prender una eliminada: 409 REGLA_ELIMINADA y sigue apagada")
    void prenderUnaEliminada() throws Exception {
        eliminar("Accion").andExpect(status().isOk());

        mvc.perform(put("/api/rules/Accion/estado").contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REGLA_ELIMINADA"));
        assertThat(states.isActive("Accion")).isFalse();
    }

    @Test
    @DisplayName("eliminar una protección: 409 PROTECCION_NO_ELIMINABLE y nada cambia")
    void eliminarUnaProteccion() throws Exception {
        eliminar("Proteccion")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROTECCION_NO_ELIMINABLE"));

        verify(repository, never()).save(any());
        assertThat(states.isActive("Proteccion")).isTrue();
    }

    @Test
    @DisplayName("eliminar o restaurar una pieza desconocida: 404")
    void desconocida() throws Exception {
        eliminar("NoExiste").andExpect(status().isNotFound());
        restaurar("NoExiste").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("restaurar: vuelve a la vista apagada; prenderla después es otra decisión")
    void restaurar() throws Exception {
        etiqueta("Accion", "{\"etiqueta\":\"Riego normal\"}").andExpect(status().isOk());
        eliminar("Accion").andExpect(status().isOk());

        restaurar("Accion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eliminada").value(false))
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.label").value("Riego normal"));
        assertThat(states.isActive("Accion")).isFalse();
        verify(historial).registrarEliminacionRegla("Ana López", "Accion", "Riego normal", "R-01", false);

        mvc.perform(put("/api/rules/Accion/estado").contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    @DisplayName("eliminar dos veces o restaurar una no eliminada no escribe nada")
    void idempotentes() throws Exception {
        restaurar("Accion").andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(true));
        verify(repository, never()).save(any());

        eliminar("Accion").andExpect(status().isOk());
        eliminar("Accion").andExpect(status().isOk()).andExpect(jsonPath("$.eliminada").value(true));
        verify(repository, times(1)).save(any());
    }
}
